package com.example.ui.main

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.FolderEntity
import com.example.data.NoteEntity
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocaleManager
import com.example.ui.markdown.MarkdownViewer
import com.example.ui.markdown.parseMarkdown
import com.example.ui.markdown.MarkdownElement
import com.example.ui.viewmodel.MainViewModel

enum class NavigationTab {
    FOLDERS, SEARCH, SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp > 720

    // ViewModel States
    val language by viewModel.appLanguage.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val looseNotes by viewModel.looseNotes.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val pendingLinkCreationTitle by viewModel.pendingLinkCreationTitle.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Screen navigation tabs (Bottom nav fallback)
    var currentTab by remember { mutableStateOf(NavigationTab.FOLDERS) }

    // Folder Expansion States
    var expandedFolders = remember { mutableStateMapOf<Int, Boolean>() }

    // New element dialog inputs
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewNoteDialog by remember { mutableStateOf(false) }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteContent by remember { mutableStateOf("") }
    var newNoteTags by remember { mutableStateOf("") }
    var newNoteFolderId by remember { mutableStateOf<Int?>(null) }

    // Note Edit state
    var isEditingNote by remember { mutableStateOf(false) }
    var editNoteTitle by remember { mutableStateOf("") }
    var editNoteContent by remember { mutableStateOf("") }
    var editNoteTags by remember { mutableStateOf("") }
    var editNoteFolderId by remember { mutableStateOf<Int?>(null) }

    // Table of Contents open state
    var showTOCPanel by remember { mutableStateOf(false) }

    // Launchers for local markdown importation
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val text = inputStream.bufferedReader().use { it.readText() }
                        // Query original filename
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        var displayName = "ImportedNote.md"
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val colIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (colIdx >= 0) {
                                    displayName = it.getString(colIdx)
                                }
                            }
                        }
                        viewModel.importMarkdownFile(displayName, text)
                        Toast.makeText(
                            context,
                            LocaleManager.getString("import_success", language),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        LocaleManager.getString("import_error", language),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )

    // Helper functions
    val getString: (String) -> String = { key ->
        LocaleManager.getString(key, language)
    }

    // Modal layouts triggers
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FF))
    ) {
        // Layout Split for tablet or general view
        if (isTablet) {
            // Side-by-side List-Detail Canonical Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Navigation controls + list
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .border(width = 1.dp, color = Color(0xFFC8C4D5))
                ) {
                    AdaptiveSidebarDashboard(
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        language = language,
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.updateSearchQuery(it) },
                        allFolders = allFolders,
                        allNotes = allNotes,
                        looseNotes = looseNotes,
                        expandedFolders = expandedFolders,
                        onFolderToggle = { folderId ->
                            expandedFolders[folderId] = !(expandedFolders[folderId] ?: false)
                        },
                        onNoteClick = { viewModel.selectNote(it) },
                        onFolderFocus = { viewModel.selectFolder(it) },
                        onImportTrigger = {
                            importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                        },
                        onNewFolderTrigger = { showNewFolderDialog = true },
                        onNewNoteTrigger = {
                            newNoteFolderId = null
                            showNewNoteDialog = true
                        },
                        viewModel = viewModel
                    )
                }

                // Right Column: Detail Content Reader/Editor
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .background(Color.White)
                ) {
                    when {
                        selectedNote != null -> {
                            val activeNote = selectedNote!!
                            if (isEditingNote) {
                                NoteInlineEditor(
                                    title = editNoteTitle,
                                    content = editNoteContent,
                                    tags = editNoteTags,
                                    folderId = editNoteFolderId,
                                    allFolders = allFolders,
                                    getString = getString,
                                    onTitleChange = { editNoteTitle = it },
                                    onContentChange = { editNoteContent = it },
                                    onTagsChange = { editNoteTags = it },
                                    onFolderIdChange = { editNoteFolderId = it },
                                    onCancel = { isEditingNote = false },
                                    onSave = {
                                        viewModel.updateNoteContent(
                                            note = activeNote,
                                            newTitle = editNoteTitle,
                                            newContent = editNoteContent,
                                            newTags = editNoteTags,
                                            folderId = editNoteFolderId
                                        )
                                        isEditingNote = false
                                    },
                                    onDelete = {
                                        viewModel.deleteNote(activeNote)
                                        isEditingNote = false
                                    }
                                )
                            } else {
                                 NoteViewerScreen(
                                    note = activeNote,
                                    allFolders = allFolders,
                                    language = language,
                                    getString = getString,
                                    onBack = { viewModel.closeDetails() },
                                    onEdit = {
                                        editNoteTitle = activeNote.title
                                        editNoteContent = activeNote.content
                                        editNoteTags = activeNote.tags
                                        editNoteFolderId = activeNote.folderId
                                        isEditingNote = true
                                    },
                                    onDoubleLinkClick = { viewModel.handleDoubleLinkClick(it) },
                                    showTOC = showTOCPanel,
                                    onToggleTOC = { showTOCPanel = it },
                                    onMoveToFolder = { newFolderId ->
                                        viewModel.updateNoteContent(
                                            note = activeNote,
                                            newTitle = activeNote.title,
                                            newContent = activeNote.content,
                                            newTags = activeNote.tags,
                                            folderId = newFolderId
                                        )
                                    }
                                )
                            }
                        }

                        selectedFolder != null -> {
                            FolderDetailView(
                                folder = selectedFolder!!,
                                allNotes = allNotes,
                                getString = getString,
                                onNoteClick = { viewModel.selectNote(it) },
                                onAddNoteToFolder = {
                                    newNoteFolderId = selectedFolder!!.id
                                    showNewNoteDialog = true
                                },
                                onBack = { viewModel.closeDetails() },
                                onDeleteFolder = {
                                    viewModel.deleteFolder(selectedFolder!!.id)
                                }
                            )
                        }

                        else -> {
                            EmptyStateDetailPlaceholder(getString = getString)
                        }
                    }
                }
            }
        } else {
            // Handheld layout (Mobile UI with Overlay flow)
            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedNote != null) {
                    // Note screen overlay
                    val activeNote = selectedNote!!
                    if (isEditingNote) {
                        NoteInlineEditor(
                            title = editNoteTitle,
                            content = editNoteContent,
                            tags = editNoteTags,
                            folderId = editNoteFolderId,
                            allFolders = allFolders,
                            getString = getString,
                            onTitleChange = { editNoteTitle = it },
                            onContentChange = { editNoteContent = it },
                            onTagsChange = { editNoteTags = it },
                            onFolderIdChange = { editNoteFolderId = it },
                            onCancel = { isEditingNote = false },
                            onSave = {
                                viewModel.updateNoteContent(
                                    note = activeNote,
                                    newTitle = editNoteTitle,
                                    newContent = editNoteContent,
                                    newTags = editNoteTags,
                                    folderId = editNoteFolderId
                                )
                                isEditingNote = false
                            },
                            onDelete = {
                                viewModel.deleteNote(activeNote)
                                isEditingNote = false
                            }
                        )
                    } else {
                        NoteViewerScreen(
                            note = activeNote,
                            allFolders = allFolders,
                            language = language,
                            getString = getString,
                            onBack = { viewModel.closeDetails() },
                            onEdit = {
                                editNoteTitle = activeNote.title
                                editNoteContent = activeNote.content
                                editNoteTags = activeNote.tags
                                editNoteFolderId = activeNote.folderId
                                isEditingNote = true
                            },
                            onDoubleLinkClick = { viewModel.handleDoubleLinkClick(it) },
                            showTOC = showTOCPanel,
                            onToggleTOC = { showTOCPanel = it },
                            onMoveToFolder = { newFolderId ->
                                viewModel.updateNoteContent(
                                    note = activeNote,
                                    newTitle = activeNote.title,
                                    newContent = activeNote.content,
                                    newTags = activeNote.tags,
                                    folderId = newFolderId
                                )
                            }
                        )
                    }
                } else if (selectedFolder != null) {
                    // Folder details overlay
                    FolderDetailView(
                        folder = selectedFolder!!,
                        allNotes = allNotes,
                        getString = getString,
                        onNoteClick = { viewModel.selectNote(it) },
                        onAddNoteToFolder = {
                            newNoteFolderId = selectedFolder!!.id
                            showNewNoteDialog = true
                        },
                        onBack = { viewModel.closeDetails() },
                        onDeleteFolder = {
                            viewModel.deleteFolder(selectedFolder!!.id)
                        }
                    )
                } else {
                    // Tab-driven dashboards
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header info
                        HeaderTitleBar(language = language, onNewNote = {
                            newNoteFolderId = null
                            showNewNoteDialog = true
                        })

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                NavigationTab.FOLDERS -> {
                                    FoldersDashboardPart(
                                        folders = allFolders,
                                        notes = allNotes,
                                        looseNotes = looseNotes,
                                        expandedFolders = expandedFolders,
                                        language = language,
                                        getString = getString,
                                        onFolderToggle = { folderId ->
                                            expandedFolders[folderId] = !(expandedFolders[folderId] ?: false)
                                        },
                                        onFolderFocus = { viewModel.selectFolder(it) },
                                        onNoteClick = { viewModel.selectNote(it) },
                                        onImportClick = {
                                            importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                                        },
                                        onNewFolderClick = { showNewFolderDialog = true }
                                    )
                                }

                                NavigationTab.SEARCH -> {
                                    SearchDashboardPart(
                                        searchQuery = searchQuery,
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        allNotes = allNotes,
                                        getString = getString,
                                        onNoteSelect = { viewModel.selectNote(it) }
                                    )
                                }

                                NavigationTab.SETTINGS -> {
                                    SettingsDashboardPart(
                                        language = language,
                                        onLanguageToggle = { viewModel.setLanguage(it) },
                                        getString = getString,
                                        onImportRaw = {
                                            importLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                                        }
                                    )
                                }
                            }
                        }

                        // Bottom Navigation Drawer (Mobile scale only)
                        BottomNavigationFooter(
                            currentTab = currentTab,
                            onTabSelect = { currentTab = it },
                            getString = getString
                        )
                    }
                }
            }
        }

        // --- DIALOG MODULES ---

        // Obsidian Double-Link auto creation prompt dialogue
        if (pendingLinkCreationTitle != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelLinkNoteCreation() },
                title = { Text(text = getString("new_note"), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = String.format(
                            getString("create_note_prompt"),
                            pendingLinkCreationTitle
                        )
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                        onClick = { viewModel.confirmLinkNoteCreation(pendingLinkCreationTitle!!) }
                    ) {
                        Text(text = getString("create"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelLinkNoteCreation() }) {
                        Text(text = getString("cancel"), color = Color(0xFF1F108E))
                    }
                }
            )
        }

        // New Folder insertion dialog dialog
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text(text = getString("new_folder"), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(text = getString("enter_folder_name")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1F108E),
                            focusedLabelColor = Color(0xFF1F108E)
                        )
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                viewModel.createFolder(newFolderName.trim())
                                newFolderName = ""
                                showNewFolderDialog = false
                            }
                        }
                    ) {
                        Text(text = getString("save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) {
                        Text(text = getString("cancel"), color = Color(0xFF1F108E))
                    }
                }
            )
        }

        // New Note insertion dialog
        if (showNewNoteDialog) {
            Dialog(onDismissRequest = { showNewNoteDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = getString("new_note"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F108E)
                        )

                        OutlinedTextField(
                            value = newNoteTitle,
                            onValueChange = { newNoteTitle = it },
                            label = { Text(text = getString("title")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1F108E),
                                focusedLabelColor = Color(0xFF1F108E)
                            )
                        )

                        OutlinedTextField(
                            value = newNoteTags,
                            onValueChange = { newNoteTags = it },
                            label = { Text(text = getString("tags")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1F108E),
                                focusedLabelColor = Color(0xFF1F108E)
                            )
                        )

                        // Folder selection dropdown
                        var foldExpanded by remember { mutableStateOf(false) }
                        val activeFold = allFolders.find { it.id == newNoteFolderId }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = activeFold?.name ?: getString("loose_notes"),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(text = getString("folders")) },
                                trailingIcon = {
                                    IconButton(onClick = { foldExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = foldExpanded,
                                onDismissRequest = { foldExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(getString("loose_notes")) },
                                    onClick = {
                                        newNoteFolderId = null
                                        foldExpanded = false
                                    }
                                )
                                allFolders.forEach { f ->
                                    DropdownMenuItem(
                                        text = { Text(f.name) },
                                        onClick = {
                                            newNoteFolderId = f.id
                                            foldExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newNoteContent,
                            onValueChange = { newNoteContent = it },
                            label = { Text(text = getString("content")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1F108E),
                                focusedLabelColor = Color(0xFF1F108E)
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showNewNoteDialog = false }) {
                                Text(text = getString("cancel"), color = Color(0xFF1F108E))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                                onClick = {
                                    if (newNoteTitle.isNotBlank()) {
                                        viewModel.createNote(
                                            title = newNoteTitle.trim(),
                                            content = newNoteContent,
                                            tags = newNoteTags,
                                            folderId = newNoteFolderId
                                        )
                                        newNoteTitle = ""
                                        newNoteContent = ""
                                        newNoteTags = ""
                                        showNewNoteDialog = false
                                    }
                                }
                            ) {
                                Text(text = getString("save"))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tablet-adaptive layout panel
@Composable
fun AdaptiveSidebarDashboard(
    currentTab: NavigationTab,
    onTabChange: (NavigationTab) -> Unit,
    language: AppLanguage,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    allFolders: List<FolderEntity>,
    allNotes: List<NoteEntity>,
    looseNotes: List<NoteEntity>,
    expandedFolders: Map<Int, Boolean>,
    onFolderToggle: (Int) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    onFolderFocus: (FolderEntity) -> Unit,
    onImportTrigger: () -> Unit,
    onNewFolderTrigger: () -> Unit,
    onNewNoteTrigger: () -> Unit,
    viewModel: MainViewModel
) {
    val getString: (String) -> String = { key ->
        LocaleManager.getString(key, language)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF4FF))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFF1F108E)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getString("app_title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1F108E)
                )
            }

            // Language quickswitch button
            IconButton(onClick = {
                viewModel.setLanguage(
                    if (language == AppLanguage.CHINESE) AppLanguage.ENGLISH else AppLanguage.CHINESE
                )
            }) {
                Icon(Icons.Default.Language, "lang", tint = Color(0xFF1F108E))
            }
        }

        // Permanent side selection list
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf(
                NavigationTab.FOLDERS to Icons.Default.Folder,
                NavigationTab.SETTINGS to Icons.Default.Settings
            )
            tabs.forEach { (tab, icon) ->
                val isActive = currentTab == tab
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF3730A3) else Color.White)
                        .clickable { onTabChange(tab) }
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) Color.White else Color(0xFF5C5F61)
                    )
                }
            }
        }

        Divider(color = Color(0xFFC8C4D5))

        // Dynamic viewport
        Box(modifier = Modifier.weight(1f)) {
            when (currentTab) {
                NavigationTab.SEARCH -> {
                    // Handled natively in search view model
                }
                NavigationTab.FOLDERS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F108E)),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(8.dp)),
                                onClick = onImportTrigger
                            ) {
                                Icon(Icons.Default.Unarchive, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getString("import_btn"), fontSize = 12.sp)
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                                modifier = Modifier.weight(1f),
                                onClick = onNewFolderTrigger
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(getString("new_folder"), fontSize = 12.sp)
                            }
                        }

                        // Local search field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text(getString("search_hint"), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1F108E),
                                unfocusedBorderColor = Color(0xFFC8C4D5)
                            ),
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF777584)) }
                        )

                        // Scrollable navigation list
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Folders section
                            item {
                                Text(
                                    text = getString("folders"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF464553)
                                )
                            }

                            items(allFolders) { folder ->
                                val notesInFolder = allNotes.filter { it.folderId == folder.id }
                                val isExpanded = expandedFolders[folder.id] ?: false

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(8.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onFolderToggle(folder.id) }
                                            .padding(12.dp)
                                    ) {
                                        Icon(Icons.Default.Folder, null, tint = Color(0xFF1F108E))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = folder.name,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0B1C30),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(Color(0xFFEFF4FF))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${notesInFolder.size}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color(0xFF777584)
                                        )
                                    }

                                    if (isExpanded) {
                                        notesInFolder.forEach { note ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onNoteClick(note) }
                                                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Description,
                                                    null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF777584)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = note.title,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = Color(0xFF464553)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Loose notes section
                            item {
                                Text(
                                    text = getString("loose_notes"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF464553),
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }

                            items(looseNotes) { note ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(8.dp))
                                        .clickable { onNoteClick(note) }
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.StickyNote2, null, tint = Color(0xFF5C5F61))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = note.title,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF0B1C30)
                                    )
                                }
                            }
                        }
                    }
                }

                NavigationTab.SETTINGS -> {
                    SettingsDashboardPart(
                        language = language,
                        onLanguageToggle = { viewModel.setLanguage(it) },
                        getString = getString,
                        onImportRaw = onImportTrigger,
                        viewModel = viewModel
                    )
                }
            }
        }

        // New Note float FAB
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            onClick = onNewNoteTrigger
        ) {
            Icon(Icons.Default.Edit, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(getString("new_note"))
        }
    }
}

@Composable
fun HeaderTitleBar(
    language: AppLanguage,
    onNewNote: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = Color(0xFF1F108E)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (language == AppLanguage.CHINESE) "我的文本文档" else "My Folders",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1F108E)
            )
        }

        IconButton(onClick = onNewNote) {
            Icon(Icons.Default.Edit, null, tint = Color(0xFF1F108E))
        }
    }
}

@Composable
fun FoldersDashboardPart(
    folders: List<FolderEntity>,
    notes: List<NoteEntity>,
    looseNotes: List<NoteEntity>,
    expandedFolders: Map<Int, Boolean>,
    language: AppLanguage,
    getString: (String) -> String,
    onFolderToggle: (Int) -> Unit,
    onFolderFocus: (FolderEntity) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    onImportClick: () -> Unit,
    onNewFolderClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Workspace intro text
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = getString("workspace"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F108E),
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = getString("knowledge_title"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B1C30)
                )
            }
        }

        // Action Buttons Row
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F108E)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(12.dp)),
                    onClick = onImportClick
                ) {
                    Icon(Icons.Default.Unarchive, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getString("import_btn"), fontWeight = FontWeight.SemiBold)
                }

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = onNewFolderClick
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getString("new_folder"), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Folders Section list
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF777584), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = getString("folders"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF464553)
                )
            }
        }

        items(folders) { folder ->
            val notesInFolder = notes.filter { it.folderId == folder.id }
            val isExpanded = expandedFolders[folder.id] ?: false

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = Color(0xFFC8C4D5), shape = RoundedCornerShape(12.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFolderToggle(folder.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFF1F108E)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = folder.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0B1C30),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFEFF4FF))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${notesInFolder.size} " + if (language == AppLanguage.CHINESE) "篇" else "NOTES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F108E)
                            )
                        }
                        IconButton(onClick = { onFolderFocus(folder) }) {
                            Icon(Icons.Default.OpenInNew, null, tint = Color(0xFF1F108E), modifier = Modifier.size(18.dp))
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF777584)
                        )
                    }

                    if (isExpanded) {
                        Divider(color = Color(0xFFEFF4FF))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFEFF4FF))
                                .padding(vertical = 4.dp)
                        ) {
                            if (notesInFolder.isEmpty()) {
                                Text(
                                    text = "No notes inside this folder.",
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                                )
                            } else {
                                notesInFolder.forEach { note ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNoteClick(note) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Description, null, tint = Color(0xFF1F108E), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = note.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF0B1C30)
                                            )
                                            if (note.tags.isNotBlank()) {
                                                Text(
                                                    text = note.tags.split(",").joinToString(" ") { "#$it" },
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1F108E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loose Notes Title
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = Color(0xFF777584), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = getString("loose_notes"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF464553)
                )
            }
        }

        // Loose notes grid list card representation
        items(looseNotes) { note ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNoteClick(note) }
                    .border(width = 1.dp, color = Color(0xFFC8C4D5), shape = RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = note.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0B1C30)
                        )
                        Icon(Icons.Default.StickyNote2, null, tint = Color(0xFF777584), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (note.content.length > 80) note.content.take(80) + "..." else note.content,
                        fontSize = 13.sp,
                        color = Color(0xFF464553),
                        maxLines = 2,
                        minLines = 2
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

// Global search interface tab layout
@Composable
fun SearchDashboardPart(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    allNotes: List<NoteEntity>,
    getString: (String) -> String,
    onNoteSelect: (NoteEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getString("search"),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0B1C30)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(getString("search_hint")) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF1F108E),
                focusedLabelColor = Color(0xFF1F108E)
            ),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF777584)) }
        )

        val filteredNotes = if (searchQuery.isBlank()) {
            allNotes
        } else {
            allNotes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.content.contains(searchQuery, ignoreCase = true) ||
                        it.tags.contains(searchQuery, ignoreCase = true)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredNotes) { note ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNoteSelect(note) }
                        .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = note.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0B1C30)
                        )
                        if (note.tags.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                note.tags.split(",").forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF4FF))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "#$tag", fontSize = 10.sp, color = Color(0xFF1F108E))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tablet empty state presentation card
@Composable
fun EmptyStateDetailPlaceholder(
    getString: (String) -> String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = Color(0xFFC8C4D5),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Select an article or folder to display",
                fontSize = 16.sp,
                color = Color(0xFF777584)
            )
        }
    }
}

// Folder Focus list display
@Composable
fun FolderDetailView(
    folder: FolderEntity,
    allNotes: List<NoteEntity>,
    getString: (String) -> String,
    onNoteClick: (NoteEntity) -> Unit,
    onAddNoteToFolder: () -> Unit,
    onBack: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    val folderNotes = allNotes.filter { it.folderId == folder.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = Color(0xFF1F108E))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.Folder, null, tint = Color(0xFF1F108E), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F108E),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDeleteFolder) {
                Icon(Icons.Default.Delete, "delete folder", tint = Color.Red)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${folderNotes.size} Notes stored inside",
                fontWeight = FontWeight.Medium,
                color = Color(0xFF464553)
            )

            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                onClick = onAddNoteToFolder
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(getString("new_note"))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(folderNotes) { note ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNoteClick(note) }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = note.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0B1C30)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (note.tags.isNotBlank()) {
                            Text(
                                text = note.tags.split(",").joinToString(" ") { "#$it" },
                                fontSize = 12.sp,
                                color = Color(0xFF1F108E)
                            )
                        }
                    }
                }
            }
        }
    }
}

// In-app localized Settings screen module
@Composable
fun SettingsDashboardPart(
    language: AppLanguage,
    onLanguageToggle: (AppLanguage) -> Unit,
    getString: (String) -> String,
    onImportRaw: () -> Unit,
    viewModel: MainViewModel? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getString("settings"),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0B1C30)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFC8C4D5), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Language Option toggle
                Column {
                    Text(
                        text = getString("language_setting"),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B1C30)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // English Button
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (language == AppLanguage.ENGLISH) Color(0xFF1F108E) else Color(0xFFEFF4FF),
                                contentColor = if (language == AppLanguage.ENGLISH) Color.White else Color(0xFF1F108E)
                            ),
                            onClick = { onLanguageToggle(AppLanguage.ENGLISH) }
                        ) {
                            Text("English")
                        }

                        // Chinese Button
                        Button(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (language == AppLanguage.CHINESE) Color(0xFF1F108E) else Color(0xFFEFF4FF),
                                contentColor = if (language == AppLanguage.CHINESE) Color.White else Color(0xFF1F108E)
                            ),
                            onClick = { onLanguageToggle(AppLanguage.CHINESE) }
                        ) {
                            Text("简体中文")
                        }
                    }
                }

                Divider(color = Color(0xFFEFF4FF))

                // Importer setting
                Column {
                    Text(
                        text = "Imports and Data Control",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B1C30)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                        onClick = onImportRaw
                    ) {
                        Icon(Icons.Default.Backup, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getString("import_md"))
                    }
                }
            }
        }
    }
}

// Markdown Note Reader Screen Visual layout (matches Image 1)
@Composable
fun NoteViewerScreen(
    note: NoteEntity,
    allFolders: List<FolderEntity>,
    language: AppLanguage,
    getString: (String) -> String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDoubleLinkClick: (String) -> Unit,
    showTOC: Boolean,
    onToggleTOC: (Boolean) -> Unit,
    onMoveToFolder: (Int?) -> Unit
) {
    val foldersMatchesName = allFolders.find { it.id == note.folderId }?.name ?: getString("loose_notes")
    var showMoveDialog by remember { mutableStateOf(false) }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = {
                Text(
                    text = getString("add_to_folder"),
                    color = Color(0xFF0B1C30),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Option 1: Loose Notes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onMoveToFolder(null)
                                showMoveDialog = false
                            }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFF777584),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = getString("loose_notes"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0B1C30)
                        )
                    }

                    // Option 2: All existing folders
                    allFolders.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onMoveToFolder(folder.id)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = Color(0xFF1F108E),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0B1C30)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text(
                        text = getString("cancel"),
                        color = Color(0xFF1F108E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    // Retrieve Toc Headers from text content dynamically
    val parsedHeaderElements = remember(note.content) {
        parseMarkdown(note.content).filterIsInstance<MarkdownElement.Header>()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Note Viewer Actions TopBar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = Color(0xFF1F108E))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "edit", tint = Color(0xFF1F108E))
                    }
                    IconButton(onClick = { onToggleTOC(!showTOC) }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "toc toggle",
                            tint = if (showTOC) Color(0xFF1F108E) else Color(0xFF777584)
                        )
                    }
                }
            }

            // Folder & Move actions Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "folder",
                        tint = Color(0xFF1F108E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = foldersMatchesName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F108E),
                        letterSpacing = 1.sp
                    )
                }

                TextButton(
                    onClick = { showMoveDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "move note",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF1F108E)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = getString("add_to_folder"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F108E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = note.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0B1C30)
            )

            // Dynamic tags pills
            if (note.tags.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    note.tags.split(",").forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFEFF4FF))
                                .border(1.dp, Color(0xFFC8C4D5), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F108E)
                            )
                        }
                    }
                }
            }

            Divider(color = Color(0xFFC8C4D5), modifier = Modifier.padding(vertical = 12.dp))

            // Scrollable Read content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownViewer(
                    content = note.content,
                    onLinkClick = onDoubleLinkClick,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Table of Contents collapsible Side slide Panel
        if (showTOC) {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .border(1.dp, Color(0xFFC8C4D5))
                    .background(Color(0xFFEFF4FF))
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = getString("toc"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1F108E)
                        )
                        IconButton(onClick = { onToggleTOC(false) }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(parsedHeaderElements) { elem ->
                            val paddingStart = when (elem.level) {
                                1 -> 0.dp
                                2 -> 12.dp
                                else -> 24.dp
                            }
                            Text(
                                text = elem.text,
                                fontSize = 12.sp,
                                color = Color(0xFF464553),
                                modifier = Modifier
                                    .padding(start = paddingStart)
                                    .clickable { /* Jumps or visual indicator */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Inline content editor details screen
@Composable
fun NoteInlineEditor(
    title: String,
    content: String,
    tags: String,
    folderId: Int?,
    allFolders: List<FolderEntity>,
    getString: (String) -> String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onFolderIdChange: (Int?) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = getString("edit_note"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F108E)
            )

            Row {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "delete note", tint = Color.Red)
                }
                TextButton(onClick = onCancel) {
                    Text(getString("cancel"), color = Color(0xFF1F108E))
                }
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F108E)),
                    onClick = onSave
                ) {
                    Text(getString("save"))
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(getString("title")) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = tags,
            onValueChange = onTagsChange,
            label = { Text(getString("tags")) },
            modifier = Modifier.fillMaxWidth()
        )

        // Dropdown selector
        var foldExpanded by remember { mutableStateOf(false) }
        val activeFold = allFolders.find { it.id == folderId }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = activeFold?.name ?: getString("loose_notes"),
                onValueChange = {},
                readOnly = true,
                label = { Text(text = getString("folders")) },
                trailingIcon = {
                    IconButton(onClick = { foldExpanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = foldExpanded,
                onDismissRequest = { foldExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(getString("loose_notes")) },
                    onClick = {
                        onFolderIdChange(null)
                        foldExpanded = false
                    }
                )
                allFolders.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.name) },
                        onClick = {
                            onFolderIdChange(f.id)
                            foldExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text(getString("content")) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

// Compact screen standard Bottom Navigation bar
@Composable
fun BottomNavigationFooter(
    currentTab: NavigationTab,
    onTabSelect: (NavigationTab) -> Unit,
    getString: (String) -> String
) {
    NavigationBar(
        containerColor = Color.White,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationBarItem(
            selected = currentTab == NavigationTab.FOLDERS,
            onClick = { onTabSelect(NavigationTab.FOLDERS) },
            icon = { Icon(Icons.Default.Folder, null) },
            label = { Text(getString("folders"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color(0xFF1F108E),
                indicatorColor = Color(0xFF3730A3),
                unselectedIconColor = Color(0xFF777584),
                unselectedTextColor = Color(0xFF777584)
            )
        )

        NavigationBarItem(
            selected = currentTab == NavigationTab.SEARCH,
            onClick = { onTabSelect(NavigationTab.SEARCH) },
            icon = { Icon(Icons.Default.Search, null) },
            label = { Text(getString("search"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color(0xFF1F108E),
                indicatorColor = Color(0xFF3730A3),
                unselectedIconColor = Color(0xFF777584),
                unselectedTextColor = Color(0xFF777584)
            )
        )

        NavigationBarItem(
            selected = currentTab == NavigationTab.SETTINGS,
            onClick = { onTabSelect(NavigationTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text(getString("settings"), fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color(0xFF1F108E),
                indicatorColor = Color(0xFF3730A3),
                unselectedIconColor = Color(0xFF777584),
                unselectedTextColor = Color(0xFF777584)
            )
        )
    }
}
