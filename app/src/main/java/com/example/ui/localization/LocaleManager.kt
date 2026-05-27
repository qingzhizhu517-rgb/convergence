package com.example.ui.localization

enum class AppLanguage {
    ENGLISH, CHINESE
}

object LocaleManager {
    private val enStrings = mapOf(
        "app_title" to "My Folders",
        "workspace" to "WORKSPACE",
        "knowledge_title" to "Knowledge Convergence",
        "import_btn" to "Import",
        "new_folder" to "New Folder",
        "new_note" to "New Note",
        "folders" to "Folders",
        "loose_notes" to "Loose Notes",
        "search" to "Search",
        "settings" to "Settings",
        "graph" to "Graph View",
        "language_setting" to "Language (Bilingual)",
        "edit_note" to "Edit File",
        "toc" to "Table of Contents",
        "back" to "Back",
        "save" to "Save",
        "title" to "Title",
        "content" to "Content (Markdown)",
        "tags" to "Tags (Comma-separated)",
        "delete" to "Delete Note",
        "delete_folder" to "Delete Folder",
        "import_md" to "Import Markdown File",
        "import_success" to "Markdown file imported successfully!",
        "import_error" to "Failed to import Markdown file.",
        "search_hint" to "Search folders or notes...",
        "create_note_prompt" to "Double-link '%s' target not found. Create standard new note?",
        "create" to "Create",
        "cancel" to "Cancel",
        "enter_folder_name" to "Enter new folder name",
        "theme" to "Theme",
        "all_notes" to "All Notes",
        "add_to_folder" to "Add to Folder"
    )

    private val zhStrings = mapOf(
        "app_title" to "我的文件夹",
        "workspace" to "工作空间",
        "knowledge_title" to "知识融合",
        "import_btn" to "本地导入",
        "new_folder" to "新建文件夹",
        "new_note" to "新建笔记",
        "folders" to "文件夹",
        "loose_notes" to "零碎笔记",
        "search" to "全局检索",
        "settings" to "设置中心",
        "graph" to "关系图谱",
        "language_setting" to "应用语言 (Language)",
        "edit_note" to "编辑笔记",
        "toc" to "文章大纲",
        "back" to "返回",
        "save" to "保存",
        "title" to "文章标题",
        "content" to "正文内容 (Markdown)",
        "tags" to "标签 (逗号分隔)",
        "delete" to "删除笔记",
        "delete_folder" to "删除该文件夹",
        "import_md" to "导入 markdown 本地文件",
        "import_success" to "Markdown 文件成功导入入库！",
        "import_error" to "导入 Markdown 文件失败，请重试。",
        "search_hint" to "搜索文件夹、文章标题或内容...",
        "create_note_prompt" to "双向链接 '%s' 指向的文件不存在，是否立即创建该同名笔记？",
        "create" to "立即创建",
        "cancel" to "取消",
        "enter_folder_name" to "请输入新文件夹名称",
        "theme" to "界面主题色",
        "all_notes" to "全部笔记",
        "add_to_folder" to "添加到文件夹"
    )

    fun getString(key: String, lang: AppLanguage): String {
        return if (lang == AppLanguage.CHINESE) {
            zhStrings[key] ?: enStrings[key] ?: key
        } else {
            enStrings[key] ?: key
        }
    }
}
