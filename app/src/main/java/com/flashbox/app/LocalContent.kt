package com.flashbox.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Resolves & serves local SWF files picked via the Storage Access Framework
 * (single files or whole folders). Persistable URI permissions are taken in
 * MainActivity so entries survive restarts; here we only scan & open streams.
 */
object LocalContent {

    const val MIME_SWF = "application/x-shockwave-flash"

    /** Recursively scan a folder tree for .swf files; returns folder + file entries. */
    fun scanTree(context: Context, treeUri: Uri, folderName: String): JSONArray {
        val out = JSONArray()
        val folderId = "fld_" + System.currentTimeMillis().toString(36)
        out.put(folderEntry(folderId, folderName, treeUri.toString()))
        try {
            val rootDoc = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDoc)
            val cr = context.contentResolver
            val stack = ArrayDeque<Pair<String, String>>() // docId, name
            stack.addLast(rootDoc to folderName)
            val seen = HashSet<String>()
            while (stack.isNotEmpty()) {
                val (docId, name) = stack.removeLast()
                if (!seen.add(docId)) continue
                val curUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                cr.query(curUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val display = c.getString(1) ?: "unknown"
                        val mime = c.getString(2) ?: ""
                        val size = c.getLong(3)
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.addLast(id to display)
                        } else if (display.lowercase().endsWith(".swf")) {
                            out.put(fileEntry(
                                parentId = folderId,
                                name = display,
                                uri = docUri.toString(),
                                size = size
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // best-effort; return what we have
        }
        return out
    }

    private fun folderEntry(id: String, name: String, uri: String): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("uri", uri)
        put("isDir", true); put("kind", "tree"); put("parent", "")
    }

    private fun fileEntry(parentId: String, name: String, uri: String, size: Long): JSONObject =
        JSONObject().apply {
            put("id", "swf_" + uri.hashCode().toString().replace("-", "n") + "_" + System.nanoTime().toString(36))
            put("name", name); put("uri", uri); put("size", size)
            put("isDir", false); put("kind", "tree"); put("parent", parentId)
        }

    fun makeFileEntry(name: String, uri: String, size: Long): JSONObject = JSONObject().apply {
        put("id", "swf_" + uri.hashCode().toString().replace("-", "n"))
        put("name", name); put("uri", uri); put("size", size)
        put("isDir", false); put("kind", "file"); put("parent", "")
    }

    /** Open a stream for a library entry URI string. */
    fun openStream(context: Context, uriString: String): InputStream? = try {
        context.contentResolver.openInputStream(Uri.parse(uriString))
    } catch (e: Exception) { null }

    fun guessMime(name: String): String =
        if (name.lowercase().endsWith(".swf")) MIME_SWF else "application/octet-stream"
}
