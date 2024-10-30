package indi.pplong.composelearning.core.file.viewmodel

import android.util.Log
import androidx.compose.runtime.toMutableStateList
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import indi.pplong.composelearning.core.base.mvi.BaseViewModel
import indi.pplong.composelearning.core.base.state.LoadingState
import indi.pplong.composelearning.core.base.state.RequestingState
import indi.pplong.composelearning.core.cache.FTPClientCache
import indi.pplong.composelearning.core.cache.FTPServerPool
import indi.pplong.composelearning.core.cache.TransferStatus
import indi.pplong.composelearning.core.file.model.ActiveServer
import indi.pplong.composelearning.core.file.model.FileItemInfo
import indi.pplong.composelearning.core.file.model.toFileItemInfo
import indi.pplong.composelearning.core.load.model.TransferringFile
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

/**
 * Description:
 * @author PPLong
 * @date 9/30/24 12:16 PM
 */
@HiltViewModel(assistedFactory = FilePathViewModel.FilePathViewModelFactory::class)
class FilePathViewModel @AssistedInject constructor(
    @Assisted val host: String
) :
    BaseViewModel<FilePathUiState, FilePathUiIntent, FilePathUiEffect>() {
    private val TAG = javaClass.name
    private lateinit var cache: FTPClientCache

    init {
        Log.d(TAG, "Init ViewModfel: $host")
        launchOnIO {
            FTPServerPool.serverFTPMap.collect {
                launch {
                    it.values.firstOrNull { it.coreFTPClient.host == host }?.let {
                        cache = it
                        getCurrentListFiles()
                        Log.d(TAG, ": ${it.coreFTPClient.host}")
                        it
                    }?.downloadQueue?.collect { queue ->
                        Log.d(TAG, "$queue: download Get")
                        queue.forEach { client ->
                            launch {
                                Log.d(TAG, "Download Queue Update ${client.toString()}")
                                client.transferFileFlow.collect { file ->
                                    val index =
                                        uiState.value.fileList.indexOfLast { it.pathPrefix == file.pathPrefix && it.name == file.name }
                                    Log.d(TAG, "Change File True")
                                    takeIf { index >= 0 }?.let {
                                        setState {
                                            copy(fileList = fileList.toMutableList().apply {
                                                set(index, file)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        launchOnIO {
            FTPServerPool.serverFTPMap.collect {
                setState {
                    copy(activeList = it.values.toList().map { server ->
                        ActiveServer(
                            serverHost = server.coreFTPClient.host,
                            serverNickname = "123",
                            hasUploadNode = server.hasUploadNode,
                            hasDownloadNode = server.hasDownloadNode
                        )
                    })
                }
            }
        }
    }

    override fun initialState(): FilePathUiState {
        return FilePathUiState()
    }


    override suspend fun handleIntent(intent: FilePathUiIntent) {
        when (intent) {
            is FilePathUiIntent.MoveForward -> onPathChanged(intent.path)
            is FilePathUiIntent.Download -> {
                downloadSingleFile(intent.outputStream, intent.fileItemInfo, intent.localUri)
            }

            is FilePathUiIntent.Upload -> {
                uploadSingleFile(intent.transferringFile, intent.inputStream)
            }

            is FilePathUiIntent.DeleteFile -> deleteFile(intent.fileName)
            is FilePathUiIntent.DismissDialog -> sendEffect {
                FilePathUiEffect.DismissDeleteDialog
            }

            is FilePathUiIntent.OpenDeleteFileDialog -> {
                sendEffect {
                    FilePathUiEffect.ShowDeleteDialog(intent.fileName)
                }
            }
        }
    }

    private fun deleteFile(filePath: String) {
        launchOnIO {
            setState { copy(actionLoadingState = RequestingState.REQUEST) }
            val deleteFile = cache.coreFTPClient.deleteFile(filePath)
            setState { copy(actionLoadingState = RequestingState.DONE) }
            if (deleteFile) {
                sendEffect { FilePathUiEffect.ActionFailed("Failed") }
            }
        }

    }

    private fun downloadSingleFile(
        outputStream: OutputStream,
        fileInfo: FileItemInfo,
        localUri: String
    ) {
        val newFileInfo =
            fileInfo.copy(transferStatus = TransferStatus.Loading, localUri = localUri)
        setState {
            copy(fileList = fileList.toMutableStateList().apply {
                set(indexOf(fileInfo), newFileInfo)
            })
        }
        launchOnIO {
            cache.downloadFile(outputStream, newFileInfo)
        }
    }

    private fun uploadSingleFile(transferringFile: TransferringFile, inputStream: InputStream) {
        val file = transferringFile.copy(
            transferredFileItem = transferringFile.transferredFileItem.copy(serverHost = cache.coreFTPClient.host)
        )

        launchOnIO {
            cache.uploadFile(file, inputStream)
        }
    }

    private fun getCurrentListFiles() {
        launchOnIO {
            Log.d(TAG, "getCurrentListFiles: Try to get")
            // TODO: Handle error situation
            val prefix = cache.coreFTPClient.getCurrentPath() ?: ""

            val list = cache.coreFTPClient.getList().map {
                it.toFileItemInfo(prefix)
            }
            Log.d(TAG, "getCurrentListFiles ${list.size}")

            setState {
                copy(
                    path = prefix,
                    fileList = list,
                    loadingState = LoadingState.SUCCESS
                )
            }
        }
    }

    private fun onPathChanged(targetPath: String) {
        launchOnIO {
            cache.coreFTPClient.changeAndGetFiles(targetPath,
                onBegin = { setState { copy(loadingState = LoadingState.LOADING) } },
                onSuccess = {
                    setState {
                        copy(
                            loadingState = LoadingState.SUCCESS,
                            fileList = it.map { it.toFileItemInfo(targetPath) },
                            path = targetPath
                        )
                    }
                    Log.d(TAG, "onPathChanged: Path successfully changed")
                },
                onFail = { setState { copy(loadingState = LoadingState.FAIL) } }
            )
        }
    }

    @AssistedFactory
    interface FilePathViewModelFactory {
        fun create(host: String): FilePathViewModel
    }

}
