package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class NotesRepository(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao
) {
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val looseNotes: Flow<List<NoteEntity>> = noteDao.getLooseNotes()

    fun getNotesByFolder(folderId: Int): Flow<List<NoteEntity>> {
        return noteDao.getNotesByFolder(folderId)
    }

    suspend fun getNoteById(id: Int): NoteEntity? = noteDao.getNoteById(id)
    suspend fun getNoteByTitle(title: String): NoteEntity? = noteDao.getNoteByTitle(title)
    suspend fun getFolderByName(name: String): FolderEntity? = folderDao.getFolderByName(name)
    suspend fun getFolderById(id: Int): FolderEntity? = folderDao.getFolderById(id)

    suspend fun insertFolder(name: String): Int {
        val existing = folderDao.getFolderByName(name)
        if (existing != null) return existing.id
        return folderDao.insertFolder(FolderEntity(name = name)).toInt()
    }

    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)
    suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)
    suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)
    suspend fun deleteFolder(folderId: Int) {
        val folder = folderDao.getFolderById(folderId)
        if (folder != null) {
            folderDao.deleteFolder(folder)
        }
    }

    suspend fun seedMockDataIfEmpty() {
        val existingFolders = allFolders.first()
        val existingNotes = allNotes.first()
        if (existingFolders.isEmpty() && existingNotes.isEmpty()) {
            // Create folders
            val activeResearchId = insertFolder("Active Research")
            val productStrategyId = insertFolder("Product Strategy")

            // Create notes for "Active Research"
            insertNote(
                NoteEntity(
                    folderId = activeResearchId,
                    title = "Convergence Logic Study",
                    tags = "Scholarship,DeepWork",
                    content = """
# The Convergence of Physical and Digital Organization

The modern workspace is increasingly defined by the ability to manage abstract information as if it were tactile objects. We look for the weight of folders and the crispness of a clean page, even when they only exist as pixels on a glass screen.

## Core Design Principles

To bridge this gap, we rely on three primary pillars of digital convergence:

*   **Structural Layering**: Using tonal shifts to define depth without clutter.
*   **Contextual Focus**: Removing UI elements when the primary task is creation.
*   **Material Honesty**: Respecting the limitations and strengths of the digital medium.

> "Minimalism is not a lack of something. It's simply the perfect amount of something."

As we continue to develop these digital folder systems, the goal remains the same: clarity of thought through clarity of interface. When the tool disappears, the work begins to sing.

```
INTERNAL NOTE
Ensure the next iteration includes the responsive bento layout for folder previews.
```

![Modern workspace workspace image](https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=800)

### Digital Physicality

The goal is to evoke the feeling of high-quality bond paper through subtle shadows and deliberate whitespace.

We can jump to [[Product Strategy]] or check our [[Idea Scratchpad]] loose notes dynamically.
                    """.trimIndent()
                )
            )

            insertNote(
                NoteEntity(
                    folderId = activeResearchId,
                    title = "Meeting Notes - Project X",
                    tags = "Meeting,Updates",
                    content = """
# Meeting Notes - Project X

Discussed the upcoming roadmap for Q4 and how to link our files together.

## Discussion Points
- Integrating bidirectional folder relationships like [[Product Strategy]] and loose thoughts dynamically.
- Designing beautiful UI mockups resembling the desk lamp design model.
- Setting up English-Chinese dual localization triggers in [[Reflection log]].
                    """.trimIndent()
                )
            )

            insertNote(
                NoteEntity(
                    folderId = activeResearchId,
                    title = "Reference Material",
                    tags = "Scholarship",
                    content = """
# Reference Material

Understanding tactile physical tools:
- Standard paper sizing and grid-breaking layouts.
- Staggered animations and spring mechanics.
- Linking ideas dynamically like a second brain. Let's see [[Convergence Logic Study]].
                    """.trimIndent()
                )
            )

            // Create notes for "Product Strategy"
            insertNote(
                NoteEntity(
                    folderId = productStrategyId,
                    title = "Q4 Roadmap Planning",
                    tags = "Roadmap",
                    content = """
# Q4 Roadmap Planning

Priority 1: Implement the new collapsed-card previews inside notes.
Priority 2: Finish integrating [[Reference Material]] and backlinks structure.
Priority 3: Optimize Obsidian-inspired link node graph performance.
                    """.trimIndent()
                )
            )

            insertNote(
                NoteEntity(
                    folderId = productStrategyId,
                    title = "Brand Guidelines Update",
                    tags = "Design",
                    content = """
# Brand Guidelines Update

Refining the Material You palette integration using custom indigo tokens.
- Primary color: `#1f108e` (Royal Slate Indigo)
- Negative space margins: 20dp on mobile.
- See details in our [[Convergence Logic Study]] research document.
                    """.trimIndent()
                )
            )

            // Create Loose Notes (folderId = null)
            insertNote(
                NoteEntity(
                    folderId = null,
                    title = "Idea Scratchpad",
                    tags = "Ideas",
                    content = """
# Idea Scratchpad

Maybe add a visual hierarchy for the "Loose Notes" section using subtle background gradients and shadows.

- Consider showing bidirectional paths.
- Check [[Grocery List]] for stationary supplies.
- Reference workspace ideas in [[Convergence Logic Study]].
                    """.trimIndent()
                )
            )

            insertNote(
                NoteEntity(
                    folderId = null,
                    title = "Grocery List",
                    tags = "Personal",
                    content = """
# Grocery List

High-quality paper, indigo ink, minimal binder clips for the new workspace.
Also check out [[Brand Guidelines Update]] for aesthetic matches.
                    """.trimIndent()
                )
            )

            insertNote(
                NoteEntity(
                    folderId = null,
                    title = "Reflection log",
                    tags = "Personal",
                    content = """
# Reflection log

Reflecting on the week: productivity increased significantly after implementing minimal interfaces and Obsidian-style bi-directional file flows.

We should document how [[Meeting Notes - Project X]] shaped our current layout.
                    """.trimIndent()
                )
            )
        }
    }
}
