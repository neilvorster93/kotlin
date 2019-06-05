/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

fun <T> Module.cached(vararg dependencies: Any, provider: () -> T): T {
    return CachedValuesManager.getManager(project).cache(this, dependencies, provider)
}

fun <T> Module.cachedByRootModifications(provider: () -> T): T {
    return cached(ProjectRootModificationTracker.getInstance(project), provider = provider)
}

fun <T> Project.cached(vararg dependencies: Any, provider: () -> T): T {
    return CachedValuesManager.getManager(this).cache(this, dependencies, provider)
}

fun <T> Project.cachedByRootModifications(provider: () -> T): T {
    return cached(ProjectRootModificationTracker.getInstance(this), provider = provider)
}

private fun <T> CachedValuesManager.cache(
    holder: UserDataHolder,
    dependencies: Array<out Any>,
    provider: () -> T
): T {
    return getCachedValue(
        holder,
        getKeyForClass(provider::class.java),
        { CachedValueProvider.Result.create(provider(), dependencies) },
        false
    )
}

