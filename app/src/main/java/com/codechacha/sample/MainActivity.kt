
package com.codechacha.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.system.Os.fstatvfs
import android.system.StructStatVfs
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val PRIMARY_UUID = "primary"
    }
    private lateinit var mStorageManager: StorageManager
    private val mVolumeStats = HashMap<Uri, StructStatVfs>()
    private val mStorageVolumePaths = HashSet<String>()
    private lateinit var mStorageVolumes: List<StorageVolume>
    private var mHaveAccessToPrimary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mStorageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        mStorageVolumes = mStorageManager.storageVolumes

        val primaryVolume = mStorageManager.primaryStorageVolume
        val intent = primaryVolume.createOpenDocumentTreeIntent()
        startActivityForResult(intent, 1)


        getVolumeStats()
        showVolumeStats()
    }

    private fun getVolumeStats() {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        mStorageVolumePaths.clear()
        persistedUriPermissions.forEach {
            mStorageVolumePaths.add(it.uri.toString())
        }
        mVolumeStats.clear()
        mHaveAccessToPrimary = false
        for (storageVolume in mStorageVolumes) {
            val uuid: String = if (storageVolume.isPrimary) {
                PRIMARY_UUID
            } else {
                storageVolume.uuid!!
            }
            val volumeUri = buildVolumeUriFromUuid(uuid)
            if (mStorageVolumePaths.contains(volumeUri.toString())) {
                Log.d(TAG, "Have access to $uuid")
                if (uuid == PRIMARY_UUID) {
                    mHaveAccessToPrimary = true
                }
                val uri = buildVolumeUriFromUuid(uuid)
                val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )
                mVolumeStats[docTreeUri] = getFileStats(docTreeUri)
            } else {
                Log.d(TAG, "Don't have access to $uuid")
            }
        }
    }

    private fun showVolumeStats() {
        val sb = StringBuilder()
        if (mVolumeStats.size == 0) {
            sb.appendln("Nothing to see here...")
        } else {
            sb.appendln("All figures are in 1K blocks.")
            sb.appendln()
        }
        mVolumeStats.forEach {
            val lastSeg = it.key.lastPathSegment
            sb.appendln("Volume: $lastSeg")
            val stats = it.value
            val blockSize = stats.f_bsize
            val totalSpace = stats.f_blocks * blockSize / 1024L
            val freeSpace = stats.f_bfree * blockSize / 1024L
            val usedSpace = totalSpace - freeSpace
            sb.appendln(" Used space: ${usedSpace.sizeFormat()}")
            sb.appendln(" Free space: ${freeSpace.sizeFormat()}")
            sb.appendln("Total space: ${totalSpace.sizeFormat()}")
            sb.appendln("----------------")
        }
        Log.d(TAG, sb.toString())
        result.text = sb.toString()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "resultCode:$resultCode")
        val uri = data?.data ?: return
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)
        Log.d(TAG, "granted uri: ${uri.path}")
        getVolumeStats()
        showVolumeStats()
    }

    private fun buildVolumeUriFromUuid(uuid: String): Uri {
        return DocumentsContract.buildTreeDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            "$uuid:"
        )
    }

    private fun getFileStats(docTreeUri: Uri): StructStatVfs {
        val pfd = contentResolver.openFileDescriptor(docTreeUri, "r")!!
        return fstatvfs(pfd.fileDescriptor)
    }

    private fun Long.sizeFormat(fieldLength: Int = 12): String {
        return String.format(Locale.US, "%,${fieldLength}d", this)
    }

}
