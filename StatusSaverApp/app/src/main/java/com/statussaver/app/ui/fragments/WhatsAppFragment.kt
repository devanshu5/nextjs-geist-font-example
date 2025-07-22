package com.statussaver.app.ui.fragments

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.AdListener
import com.statussaver.app.R
import com.statussaver.app.adapters.StatusAdapter
import com.statussaver.app.databinding.FragmentWhatsappBinding
import com.statussaver.app.models.StatusModel
import com.statussaver.app.utils.PermissionUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class WhatsAppFragment : Fragment() {

    private var _binding: FragmentWhatsappBinding? = null
    private val binding get() = _binding!!
    private lateinit var statusAdapter: StatusAdapter
    private val statusList = mutableListOf<StatusModel>()
    private var interstitialAd: InterstitialAd? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWhatsappBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupAds()
        checkPermissionsAndLoadStatuses()
        
        binding.swipeRefresh.setOnRefreshListener {
            loadWhatsAppStatuses()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        statusAdapter = StatusAdapter(statusList) { status ->
            showInterstitialAndDownload(status)
        }
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = statusAdapter
        }
    }

    private fun setupAds() {
        // Banner Ad
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
        
        // Interstitial Ad
        interstitialAd = InterstitialAd(requireContext())
        interstitialAd?.adUnitId = "ca-app-pub-3940256099942544/1033173712"
        interstitialAd?.loadAd(AdRequest.Builder().build())
    }

    private fun checkPermissionsAndLoadStatuses() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                PermissionUtils.requestManageExternalStoragePermission(requireActivity())
            } else {
                loadWhatsAppStatuses()
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                loadWhatsAppStatuses()
            } else {
                PermissionUtils.requestStoragePermission(requireActivity())
            }
        }
    }

    private fun loadWhatsAppStatuses() {
        statusList.clear()
        
        val whatsappStatusPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
        } else {
            "/storage/emulated/0/WhatsApp/Media/.Statuses"
        }
        
        val statusDir = File(whatsappStatusPath)
        
        if (statusDir.exists() && statusDir.isDirectory) {
            val files = statusDir.listFiles()
            files?.forEach { file ->
                if (file.isFile && !file.name.startsWith(".nomedia")) {
                    val isVideo = file.extension.lowercase() in listOf("mp4", "3gp", "mkv", "avi")
                    statusList.add(
                        StatusModel(
                            fileName = file.name,
                            filePath = file.absolutePath,
                            isVideo = isVideo,
                            size = file.length(),
                            dateModified = file.lastModified()
                        )
                    )
                }
            }
            statusList.sortByDescending { it.dateModified }
            statusAdapter.notifyDataSetChanged()
            
            binding.emptyView.visibility = if (statusList.isEmpty()) View.VISIBLE else View.GONE
        } else {
            binding.emptyView.visibility = View.VISIBLE
            Toast.makeText(context, "WhatsApp status folder not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInterstitialAndDownload(status: StatusModel) {
        if (interstitialAd?.isLoaded == true) {
            interstitialAd?.adListener = object : AdListener() {
                override fun onAdClosed() {
                    downloadStatus(status)
                    interstitialAd?.loadAd(AdRequest.Builder().build())
                }
            }
            interstitialAd?.show()
        } else {
            downloadStatus(status)
        }
    }

    private fun downloadStatus(status: StatusModel) {
        try {
            val sourceFile = File(status.filePath)
            val fileName = "StatusSaver_${System.currentTimeMillis()}_${status.fileName}"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, 
                        if (status.isVideo) "video/*" else "image/*")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, 
                        if (status.isVideo) Environment.DIRECTORY_MOVIES + "/StatusSaver"
                        else Environment.DIRECTORY_PICTURES + "/StatusSaver")
                }
                
                val uri = requireContext().contentResolver.insert(
                    if (status.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            } else {
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (status.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    ), "StatusSaver"
                )
                
                if (!destDir.exists()) destDir.mkdirs()
                
                val destFile = File(destDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
            }
            
            Toast.makeText(context, "Status saved successfully!", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save status: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
