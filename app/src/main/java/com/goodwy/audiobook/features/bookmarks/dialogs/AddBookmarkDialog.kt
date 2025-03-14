package com.goodwy.audiobook.features.bookmarks.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.bluelinelabs.conductor.Controller
import com.goodwy.audiobook.R
import com.goodwy.audiobook.misc.DialogController

/**
 * Dialog for choosing a title for a new bookmark.
 */
class AddBookmarkDialog : DialogController() {

  @SuppressLint("CheckResult")
  override fun onCreateDialog(savedViewState: Bundle?): Dialog {
    val inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
        InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
    val dialog = MaterialDialog(activity!!).apply {

      title(R.string.bookmark)
	  cornerRadius(4f)
      input(hintRes = R.string.bookmark_edit_hint, allowEmpty = true, inputType = inputType) { _, charSequence ->
        val title = charSequence.toString()
        val callback = targetController as Callback
        callback.onBookmarkNameChosen(title)
      }
      positiveButton(R.string.dialog_confirm)
    }
    val editText = dialog.getInputField()
    editText.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        val title = editText.text.toString()
        val callback = targetController as Callback
        callback.onBookmarkNameChosen(title)
        dismissDialog()
        true
      } else false
    }
    return dialog
  }

  interface Callback {
    fun onBookmarkNameChosen(name: String)
  }

  companion object {
    operator fun <T> invoke(target: T) where T : Controller, T : Callback =
      AddBookmarkDialog().apply {
        targetController = target
      }
  }
}
