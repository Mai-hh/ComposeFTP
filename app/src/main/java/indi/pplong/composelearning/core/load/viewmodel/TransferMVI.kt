package indi.pplong.composelearning.core.load.viewmodel

import indi.pplong.composelearning.core.base.mvi.UiEffect
import indi.pplong.composelearning.core.base.mvi.UiIntent
import indi.pplong.composelearning.core.base.mvi.UiState
import indi.pplong.composelearning.core.file.model.FileItemInfo
import indi.pplong.composelearning.core.file.model.TransferredFileItem
import indi.pplong.composelearning.core.load.model.TransferringFile

/**
 * Description:
 * @author PPLong
 * @date 10/24/24 9:38 PM
 */
data class TransferUiState(
    val curIndex: Int = 0,
    val downloadFileList: List<FileItemInfo> = mutableListOf(),
    val uploadFileList: List<TransferringFile> = mutableListOf(),
    val alreadyDownloadFileList: List<TransferredFileItem> = mutableListOf(),
    val alreadyUploadFileList: List<TransferredFileItem> = mutableListOf()
) : UiState

sealed class TransferUiIntent : UiIntent {
    data class MoveForward(val path: String) : TransferUiIntent()
    data class SwitchTab(val index: Int) :
        TransferUiIntent()
}

sealed class TransferUiEffect : UiEffect {

}