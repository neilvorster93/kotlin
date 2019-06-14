/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.EntryPointsManager
import com.intellij.codeInspection.ex.EntryPointsManagerBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.script.ScriptsCompilationConfigurationUpdater
import org.jetbrains.kotlin.idea.isMainFunction
import org.jetbrains.kotlin.idea.search.findScriptsWithUsages
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReferencesSearchParameters
import org.jetbrains.kotlin.idea.search.isCheapEnoughToSearchConsideringOperators
import org.jetbrains.kotlin.idea.search.usagesSearch.dataClassComponentFunction
import org.jetbrains.kotlin.idea.search.usagesSearch.getAccessorNames
import org.jetbrains.kotlin.idea.search.usagesSearch.getClassNameForCompanionObject
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isPrivateNestedClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.isInlineClassType

class UnusedSymbolInspection : AbstractKotlinInspection() {
    companion object {
        private val javaInspection = UnusedDeclarationInspection()

        private val KOTLIN_ADDITIONAL_ANNOTATIONS = listOf("kotlin.test.*")

        private fun KtDeclaration.hasKotlinAdditionalAnnotation() =
            this is KtNamedDeclaration && checkAnnotatedUsingPatterns(this, KOTLIN_ADDITIONAL_ANNOTATIONS)

        fun isEntryPoint(declaration: KtNamedDeclaration): Boolean {
            if (declaration.hasKotlinAdditionalAnnotation()) return true
            if (declaration is KtClass && declaration.declarations.any { it.hasKotlinAdditionalAnnotation() }) return true

            // Some of the main-function-cases are covered by 'javaInspection.isEntryPoint(lightElement)' call
            // but not all of them: light method for parameterless main still points to parameterless name
            // that is not an actual entry point from Java language point of view
            if (declaration.isMainFunction()) return true

            val lightElement: PsiElement? = when (declaration) {
                is KtClassOrObject -> declaration.toLightClass()
                is KtNamedFunction, is KtSecondaryConstructor -> LightClassUtil.getLightClassMethod(declaration as KtFunction)
                is KtProperty, is KtParameter -> {
                    if (declaration is KtParameter && !declaration.hasValOrVar()) return false
                    // can't rely on light element, check annotation ourselves
                    val entryPointsManager = EntryPointsManager.getInstance(declaration.project) as EntryPointsManagerBase
                    return checkAnnotatedUsingPatterns(
                        declaration,
                        entryPointsManager.additionalAnnotations + entryPointsManager.ADDITIONAL_ANNOTATIONS
                    )
                }
                else -> return false
            }

            if (lightElement == null) return false

            if (isCheapEnoughToSearchUsages(declaration) == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) return false

            return javaInspection.isEntryPoint(lightElement)
        }

        fun isCheapEnoughToSearchUsages(declaration: KtNamedDeclaration): PsiSearchHelper.SearchCostResult {
            val project = declaration.project
            val psiSearchHelper = PsiSearchHelper.getInstance(project)

            val usedScripts = findScriptsWithUsages(declaration)
            if (usedScripts.isNotEmpty()) {
                if (ScriptsCompilationConfigurationUpdater.getInstance(declaration.project).updateDependenciesIfNeeded(usedScripts)) {
                    return PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
                }
            }

            val useScope = psiSearchHelper.getUseScope(declaration)
            if (useScope is GlobalSearchScope) {
                var zeroOccurrences = true
                for (name in listOf(declaration.name) + declaration.getAccessorNames() + listOfNotNull(declaration.getClassNameForCompanionObject())) {
                    if (name == null) continue
                    when (psiSearchHelper.isCheapEnoughToSearchConsideringOperators(name, useScope, null, null)) {
                        PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES -> {
                        } // go on, check other names
                        PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES -> zeroOccurrences = false
                        PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES -> return PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES // searching usages is too expensive; behave like it is used
                    }
                }

                if (zeroOccurrences) return PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES
            }
            return PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES
        }

        fun KtProperty.isSerializationImplicitlyUsedField(): Boolean {
            val ownerObject = getNonStrictParentOfType<KtClassOrObject>()
            return if (ownerObject is KtObjectDeclaration && ownerObject.isCompanion()) {
                val lightClass = ownerObject.getNonStrictParentOfType<KtClass>()?.toLightClass() ?: return false
                lightClass.fields.any { it.name == name && HighlightUtil.isSerializationImplicitlyUsedField(it) }
            } else
                false
        }

        fun KtNamedFunction.isSerializationImplicitlyUsedMethod(): Boolean = toLightMethods()
            .any { JavaHighlightUtil.isSerializationRelatedMethod(it, it.containingClass) }

        // variation of IDEA's AnnotationUtil.checkAnnotatedUsingPatterns()
        fun checkAnnotatedUsingPatterns(
            declaration: KtNamedDeclaration,
            annotationPatterns: Collection<String>
        ): Boolean {
            if (declaration.annotationEntries.isEmpty()) return false
            val context = declaration.analyze()
            val annotationsPresent = declaration.annotationEntries.mapNotNull {
                context[BindingContext.ANNOTATION, it]?.fqName?.asString()
            }
            if (annotationsPresent.isEmpty()) return false

            for (pattern in annotationPatterns) {
                val hasAnnotation = if (pattern.endsWith(".*")) {
                    annotationsPresent.any { it.startsWith(pattern.dropLast(1)) }
                } else {
                    pattern in annotationsPresent
                }
                if (hasAnnotation) return true
            }

            return false
        }
    }

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