/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid

class UnusedLocalSymbolInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        fun checkDeclaration(declaration: KtNamedDeclaration) {
            val message = declaration.describe()?.let { "$it is never used" } ?: return
            val identifier = declaration.nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: return
            holder.registerProblem(
                declaration,
                message,
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                identifier.textRangeIn(declaration),
                SafeDeleteFix(declaration)
            )
        }

        return object : KtVisitorVoid() {
            override fun visitBlockExpression(block: KtBlockExpression) {
                if (!ProjectRootsUtil.isInProjectSource(block)) return
                val declarations = block.statements.asSequence().filterIsInstance<KtNamedDeclaration>()
                if (declarations.none()) return
                val scope = LocalSearchScope(block)
                for (declaration in declarations) {
                    if (ReferencesSearch.search(declaration, scope).findFirst() == null) {
                        checkDeclaration(declaration)
                    }
                }
            }
        }
    }
}


