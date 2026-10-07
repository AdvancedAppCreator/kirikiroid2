package org.github.krkr2

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject

/**
 * Read-only content provider that advertises this player's external-launch
 * capabilities to game-library apps.
 *
 * Authority: `${applicationId}.capabilities`.
 *
 * A [query] returns a single row with a single `json` column holding:
 * ```
 * { "apiVersion": 1, "launch": true, "exitEvent": false, "safUri": false,
 *   "package": "<applicationId>" }
 * ```
 */
class CapabilitiesProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val json = JSONObject().apply {
            put("apiVersion", LaunchContract.API_VERSION)
            put("launch", true)
            put("exitEvent", LaunchContract.CAP_EXIT_EVENT)
            put("safUri", LaunchContract.CAP_SAF_URI)
            put("package", context?.packageName ?: "")
        }
        return MatrixCursor(arrayOf("json")).apply {
            addRow(arrayOf<Any?>(json.toString()))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY_SUFFIX"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val AUTHORITY_SUFFIX = "capabilities"
    }
}
