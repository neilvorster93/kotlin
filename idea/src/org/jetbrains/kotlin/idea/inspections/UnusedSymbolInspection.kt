/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolGlobalInspection.Companion.isSerializationImplicitlyUsedField
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolGlobalInspection.Companion.isSerializationImplicitlyUsedMethod
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.usagesSearch.dataClassComponentFunction
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isPrivateNestedClassOrObject
import org.jetbrains.kotlin.resolve.isInlineClassType

class UnusedSymbolInspection : AbstractKotlinInspection() {
    override fun runForWholeFile(): Boolean = true

    override val suppressionKey: String get() = "unused"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = namedDeclarationVisitor(fun(declaration: KtNamedDeclaration) {
        if (!checkVisibility(declaration)) return
        if (!ProjectRootsUtil.isInProjectSource(declaration)) return

        if (declaration.annotationEntries.isNotEmpty()) return
        if (declaration is KtProperty && declaration.isLocal) return
        if (declaration is KtEnumEntry) return
        if (declaration is KtObjectDeclaration && declaration.isCompanion()) return // never mark companion object as unused (there are too many reasons it can be needed for)

        if (declaration is KtProperty && declaration.isSerializationImplicitlyUsedField()) return
        if (declaration is KtNamedFunction && declaration.isSerializationImplicitlyUsedMethod()) return

        // TODO: remove after fix KT-31934
        if (checkPrivateDeclaration(declaration)) return

        // properties can be referred by component1/component2, which is too expensive to search, don't mark them as unused
        if (declaration is KtParameter && declaration.dataClassComponentFunction() != null) return
        if (declaration.isUsed()) return

        val message = declaration.describe()?.let { "$it is never used" } ?: return
        val identifier = declaration.nameIdentifier ?: (declaration as? KtConstructor<*>)?.getConstructorKeyword() ?: return
        holder.registerProblem(
            declaration,
            message,
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            identifier.textRangeIn(declaration),
            SafeDeleteFix(declaration)
        )
    })
}

private fun checkVisibility(declaration: KtNamedDeclaration): Boolean = when {
    declaration is KtClassOrObject && declaration.isTopLevel() -> false
    declaration.isPrivate() || declaration.parent is KtBlockExpression -> true
    declaration is KtTypeParameter -> (declaration.parent.parent as? KtNamedDeclaration)?.let { checkVisibility(it) } ?: false
    else -> false
}

private fun KtNamedDeclaration.isUsed(): Boolean {
    fun checkReference(ref: PsiReference): Boolean {
        if (isAncestor(ref.element)) return false // usages inside element's declaration are not counted

        if (ref.element.parent is KtValueArgumentName) return false // usage of parameter in form of named argument is not counted
        return true
    }

    val searchScope = useScope
    if (this is KtCallableDeclaration && canBeHandledByLightMethods(resolveToDescriptorIfAny())) {
        val lightMethods = toLightMethods()
        if (lightMethods.isNotEmpty()) {
            return lightMethods.any { method ->
                MethodReferencesSearch.search(method, searchScope, true).any(::checkReference)
            }
        }
    }

    return ReferencesSearch.search(KotlinReferencesSearchParameters(this, searchScope)).any(::checkReference)
}

private fun canBeHandledByLightMethods(descriptor: DeclarationDescriptor?): Boolean = when (descriptor) {
    null -> false
    is ConstructorDescriptor -> {
        val classDescriptor = descriptor.constructedClass
        !classDescriptor.isInline && classDescriptor.visibility != Visibilities.LOCAL
    }
    !is FunctionDescriptor -> true
    else -> !descriptor.hasInlineClassParameters()
}

private fun FunctionDescriptor.hasInlineClassParameters(): Boolean = when {
    dispatchReceiverParameter?.type?.isInlineClassType() == true -> true
    extensionReceiverParameter?.type?.isInlineClassType() == true -> true
    else -> valueParameters.any { it.type.isInlineClassType() }
}

private fun checkPrivateDeclaration(declaration: KtNamedDeclaration): Boolean {
    if (!declaration.isPrivateNestedClassOrObject) return false

    var hasMatch = false
    declaration.containingKtFile.importList?.acceptChildren(simpleNameExpressionRecursiveVisitor {
        if (it.getReferencedName() == declaration.name) {
            hasMatch = true
        }
    })

    return hasMatch
}
