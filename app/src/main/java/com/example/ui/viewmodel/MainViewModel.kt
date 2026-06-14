package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.localization.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = NotesRepository(database.folderDao(), database.noteDao())

    // Language State
    private val _appLanguage = MutableStateFlow(AppLanguage.CHINESE)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // Navigation and Selection States
    private val _selectedNote = MutableStateFlow<NoteEntity?>(null)
    val selectedNote: StateFlow<NoteEntity?> = _selectedNote.asStateFlow()

    private val _selectedFolder = MutableStateFlow<FolderEntity?>(null)
    val selectedFolder: StateFlow<FolderEntity?> = _selectedFolder.asStateFlow()

    // Dialog state for on-the-fly note creation during bidirectional link navigation
    private val _pendingLinkCreationTitle = MutableStateFlow<String?>(null)
    val pendingLinkCreationTitle: StateFlow<String?> = _pendingLinkCreationTitle.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Reactive lists from Database
    val allFolders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<NoteEntity>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val looseNotes: StateFlow<List<NoteEntity>> = repository.looseNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Initialize or Seed Mock Data
    init {
        viewModelScope.launch {
            repository.seedMockDataIfEmpty()
        }
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
    }

    fun selectNote(note: NoteEntity?) {
        _selectedNote.value = note
        if (note != null) {
            _selectedFolder.value = null // Close folder view if view note
        }
    }

    fun selectFolder(folder: FolderEntity?) {
        _selectedFolder.value = folder
        if (folder != null) {
            _selectedNote.value = null // Close note view if focusing folder
        }
    }

    fun closeDetails() {
        _selectedNote.value = null
        _selectedFolder.value = null
    }

    // Obsidian Link Action
    fun handleDoubleLinkClick(linkTarget: String) {
        viewModelScope.launch {
            // 1. Search if a note exists with this exact title
            val matchedNote = repository.getNoteByTitle(linkTarget)
            if (matchedNote != null) {
                selectNote(matchedNote)
                return@launch
            }

            // 2. Search if a folder exists with this exact name
            val matchedFolder = repository.getFolderByName(linkTarget)
            if (matchedFolder != null) {
                selectFolder(matchedFolder)
                return@launch
            }

            // 3. Not found: Open prompt dialog to create note with this title
            _pendingLinkCreationTitle.value = linkTarget
        }
    }

    fun confirmLinkNoteCreation(title: String) {
        viewModelScope.launch {
            val newNote = NoteEntity(
                title = title,
                content = "# $title\n\nCreated dynamically from bidirectional link reference.",
                tags = "Draft"
            )
            val insertedId = repository.insertNote(newNote)
            val createdNote = repository.getNoteById(insertedId.toInt())
            if (createdNote != null) {
                _selectedNote.value = createdNote
            }
            _pendingLinkCreationTitle.value = null
        }
    }

    fun cancelLinkNoteCreation() {
        _pendingLinkCreationTitle.value = null
    }

    private fun showToast(msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    showToast("Name cannot be empty")
                    return@launch
                }
                repository.insertFolder(trimmed)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Folder creation failed (maybe duplicate name?)")
            }
        }
    }

    fun createNote(title: String, content: String, folderId: Int?, tags: String = "") {
        viewModelScope.launch {
            try {
                val trimmedTitle = title.trim()
                if (trimmedTitle.isEmpty()) {
                    showToast("Title cannot be empty")
                    return@launch
                }
                val existing = repository.getNoteByTitle(trimmedTitle)
                if (existing != null) {
                    showToast("Note title already exists")
                    return@launch
                }
                val newNote = NoteEntity(
                    folderId = folderId,
                    title = trimmedTitle,
                    content = content,
                    tags = tags
                )
                repository.insertNote(newNote)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to create note")
            }
        }
    }

    fun updateNoteContent(note: NoteEntity, newTitle: String, newContent: String, newTags: String = "", folderId: Int?) {
        viewModelScope.launch {
            try {
                val trimmedTitle = newTitle.trim()
                if (trimmedTitle.isEmpty()) {
                    showToast("Title cannot be empty")
                    return@launch
                }
                
                // If title changed, check if new title exists in another note
                if (!trimmedTitle.equals(note.title, ignoreCase = true)) {
                    val existing = repository.getNoteByTitle(trimmedTitle)
                    if (existing != null && existing.id != note.id) {
                        showToast("Note title already exists")
                        return@launch
                    }
                }

                val updated = note.copy(
                    title = trimmedTitle,
                    content = newContent,
                    tags = newTags,
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNote(updated)
                // If the note currently viewed is this note, update state
                if (_selectedNote.value?.id == note.id) {
                    _selectedNote.value = updated
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to update note")
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            try {
                repository.deleteNote(note)
                if (_selectedNote.value?.id == note.id) {
                    _selectedNote.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to delete note")
            }
        }
    }

    fun deleteFolder(folderId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteFolder(folderId)
                if (_selectedFolder.value?.id == folderId) {
                    _selectedFolder.value = null
                }
                // Any note in the deleted folder has its folderId set to null in database due to onCascade SET_NULL
                // But let's also update the currently selected note's state if its folder was deleted
                _selectedNote.value?.let { currentNote ->
                    if (currentNote.folderId == folderId) {
                        _selectedNote.value = currentNote.copy(folderId = null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to delete folder")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Local file import handler: parses imported markdown details
    fun importMarkdownFile(fileName: String, content: String) {
        viewModelScope.launch {
            try {
                // Clean file name to get note title
                val cleanTitle = fileName.removeSuffix(".md").removeSuffix(".txt").trim()

                // Try to find if header contains '# Title'
                val headerRegex = "^#\\s+(.*)$".toRegex(RegexOption.MULTILINE)
                val titleFromContent = headerRegex.find(content)?.groupValues?.get(1)?.trim()
                val finalTitle = titleFromContent ?: cleanTitle

                // Save note to database is unique title check
                val existing = repository.getNoteByTitle(finalTitle)
                val newTitle = if (existing != null) {
                    // Try with alternative increment names
                    var suffix = 1
                    var testTitle = "$finalTitle ($suffix)"
                    while (repository.getNoteByTitle(testTitle) != null) {
                        suffix++
                        testTitle = "$finalTitle ($suffix)"
                    }
                    testTitle
                } else {
                    finalTitle
                }

                repository.insertNote(
                    NoteEntity(
                        title = newTitle,
                        content = content,
                        tags = "Imported"
                    )
                )
                showToast("Successfully imported $newTitle")
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Failed to import markdown file")
            }
        }
    }
}
