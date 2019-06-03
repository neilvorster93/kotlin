/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import org.jetbrains.kotlin.idea.scratch.getScratchPanel
import org.jetbrains.kotlin.idea.scratch.getScratchPanelFromSelectedEditor
import javax.swing.Icon

abstract class ScratchAction(message: String, icon: Icon) : AnAction(message, message, icon) {
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val scratchPanel = if (editor != null) {
            TextEditorProvider.getInstance().getTextEditor(editor).getScratchPanel()
        } else {
            e.project?.let { getScratchPanelFromSelectedEditor(it) }
        }
        e.presentation.isVisible = scratchPanel != null
    }
}