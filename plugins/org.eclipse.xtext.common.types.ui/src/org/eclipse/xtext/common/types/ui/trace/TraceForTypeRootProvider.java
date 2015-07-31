/*******************************************************************************
 * Copyright (c) 2012 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.common.types.ui.trace;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.IPath;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.BinaryType;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;
import org.eclipse.xtext.generator.trace.AbstractTraceRegion;
import org.eclipse.xtext.generator.trace.ITraceRegionProvider;
import org.eclipse.xtext.generator.trace.SourceRelativeURI;
import org.eclipse.xtext.generator.trace.TraceFileNameProvider;
import org.eclipse.xtext.generator.trace.TraceRegionSerializer;
import org.eclipse.xtext.ui.generator.trace.IEclipseTrace;
import org.eclipse.xtext.ui.generator.trace.ITraceForStorageProvider;
import org.eclipse.xtext.util.Pair;
import org.eclipse.xtext.util.Tuples;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/**
 * @author Moritz Eysholdt - Initial contribution and API
 * @noextend This class is not intended to be subclassed by clients.
 * @noinstantiate This class is not intended to be instantiated by clients.
 */
@Singleton
public class TraceForTypeRootProvider implements ITraceForTypeRootProvider {

	private static final Logger log = Logger.getLogger(TraceForTypeRootProvider.class);

	@Inject
	private TraceRegionSerializer traceRegionSerializer;

	@Inject
	private Provider<ZipFileAwareTrace> zipFileAwareTraceProvider;

	@Inject
	private Provider<FolderAwareTrace> folderAwareTraceProvider;

	@Inject
	private TraceFileNameProvider traceFileNameProvider;

	@Inject
	private ITraceForStorageProvider traceForStorageProvider;
	
	private Pair<ITypeRoot, IEclipseTrace> lruCache = null;

	protected SourceRelativeURI getPathInFragmentRoot(final ITypeRoot derivedResource, String traceSimpleFileName) {
		return new SourceRelativeURI(URI.createURI(derivedResource.getParent().getElementName().replace('.', '/') + "/" + traceSimpleFileName));
	}

	protected String getTraceSimpleFileName(final ITypeRoot derivedResource) {
		IType type = derivedResource.findPrimaryType();
		if (type == null)
			return null;
		String sourceName = ((BinaryType) type).getSourceFileName(null);
		if (sourceName == null)
			return null;

		// the primary source in the .class file is .java (JSR-45 aka SMAP scenario)
		if (sourceName.endsWith(".java")) {
			return traceFileNameProvider.getTraceFromJava(sourceName);
		}

		// xtend-as-primary-source-scenario.
		if (sourceName.endsWith(".xtend")) {
			String name = type.getElementName();
			int index = name.indexOf("$");
			if (index > 0)
				name = name.substring(0, index);
			return traceFileNameProvider.getTraceFromJava(name + ".java");
		}
		return null;
	}

	protected IPath getSourcePath(final ITypeRoot derivedJavaType) {
		IJavaElement current = derivedJavaType.getParent();
		while (current != null) {
			if (current instanceof IPackageFragmentRoot) {
				IPackageFragmentRoot fragmentRoot = (IPackageFragmentRoot) current;
				try {
					IPath attachmentPath = fragmentRoot.getSourceAttachmentPath();
					if (attachmentPath != null)
						return attachmentPath;
				} catch (JavaModelException e) {
				}
				if (current instanceof JarPackageFragmentRoot)
					return fragmentRoot.getPath();

			}
			current = current.getParent();
		}
		return null;
	}

	protected boolean isZipFile(IPath path) {
		if (path.getFileExtension() == null)
			return false;
		String ext = path.getFileExtension();
		return "jar".equalsIgnoreCase(ext) || "zip".equalsIgnoreCase(ext);
	}

	/* @Nullable */
	@Override
	public IEclipseTrace getTraceToSource(final ITypeRoot derivedJavaType) {
		if (lruCache != null && lruCache.getFirst().equals(derivedJavaType))
			return lruCache.getSecond();
		IEclipseTrace trace = createTraceToSource(derivedJavaType);
		if (derivedJavaType.isReadOnly()) {
			lruCache = Tuples.<ITypeRoot, IEclipseTrace> create(derivedJavaType, trace);
		}
		return trace;
	}

	private IEclipseTrace createTraceToSource(final ITypeRoot derivedJavaType) {
		if (derivedJavaType instanceof IClassFile)
			return getTraceToSource((IClassFile) derivedJavaType);
		if (derivedJavaType instanceof ICompilationUnit)
			return getTraceToSource((ICompilationUnit) derivedJavaType);
		throw new IllegalStateException("Unknown type " + derivedJavaType);
	}

	/* @Nullable */
	public IEclipseTrace getTraceToSource(final ICompilationUnit javaFile) {
		try {
			IResource resource = javaFile.getUnderlyingResource();
			if (resource instanceof IStorage)
				return traceForStorageProvider.getTraceToSource((IStorage) resource);
		} catch (JavaModelException e) {
			log.error("Error finding trace to source", e);
		}
		return null;
	}

	/* @Nullable */
	public IEclipseTrace getTraceToSource(final IClassFile classFile) {
		IPath sourcePath = getSourcePath(classFile);
		if (sourcePath == null)
			return null;
		IProject project = classFile.getJavaProject().getProject();
		class TraceRegionProvider implements ITraceRegionProvider {
			
			private AbstractTraceWithoutStorage trace;

			TraceRegionProvider(AbstractTraceWithoutStorage trace) {
				this.trace = trace;
			}
			
			@Override
			public AbstractTraceRegion getTraceRegion() {
				String traceSimpleFileName = getTraceSimpleFileName(classFile);
				if (traceSimpleFileName == null)
					return null;
				SourceRelativeURI traceURI = getPathInFragmentRoot(classFile, traceSimpleFileName);
				try {
					InputStream contents = trace.getContents(traceURI, trace.getLocalProject());
					if (contents != null)
						try {
							return traceRegionSerializer.readTraceRegionFrom(contents);
						} finally {
							contents.close();
						}
				} catch (IOException e) {
					log.error("Error finding trace region", e);
				}
				return null;
			}
		}

		if (isZipFile(sourcePath)) {
			ZipFileAwareTrace zipFileAwareTrace = zipFileAwareTraceProvider.get();
			zipFileAwareTrace.setProject(project);
			zipFileAwareTrace.setZipFilePath(sourcePath);
			zipFileAwareTrace.setTraceRegionProvider(new TraceRegionProvider(zipFileAwareTrace));
			return zipFileAwareTrace;
		} else {
			FolderAwareTrace folderAwareTrace = folderAwareTraceProvider.get();
			folderAwareTrace.setProject(project);
			folderAwareTrace.setRootFolder(sourcePath.toString());
			folderAwareTrace.setTraceRegionProvider(new TraceRegionProvider(folderAwareTrace));
			return folderAwareTrace;
		}
	}
}