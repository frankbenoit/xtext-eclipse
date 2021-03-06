/*******************************************************************************
 * Copyright (c) 2010, 2020 itemis AG (http://www.itemis.eu) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.xtext.ui.tests.editor.quickfix;

import static com.google.common.collect.Iterables.*;
import static com.google.common.collect.Lists.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.ui.MarkerTypes;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.model.IXtextDocument;
import org.eclipse.xtext.ui.editor.quickfix.IssueResolutionProvider;
import org.eclipse.xtext.ui.editor.validation.AnnotationIssueProcessor;
import org.eclipse.xtext.ui.editor.validation.MarkerCreator;
import org.eclipse.xtext.ui.editor.validation.XtextAnnotation;
import org.eclipse.xtext.ui.testing.AbstractQuickfixTest;
import org.eclipse.xtext.ui.testing.util.AnnotatedTextToString;
import org.eclipse.xtext.ui.testing.util.IResourcesSetupUtil;
import org.eclipse.xtext.ui.tests.quickfix.ui.tests.QuickfixCrossrefTestLanguageUiInjectorProvider;
import org.eclipse.xtext.ui.tests.quickfix.validation.QuickfixCrossrefTestLanguageValidator;
import org.eclipse.xtext.ui.util.IssueUtil;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.validation.Issue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.Lists;

/**
 * @author Jan Koehnlein - Initial contribution and API
 */
@RunWith(XtextRunner.class)
@InjectWith(QuickfixCrossrefTestLanguageUiInjectorProvider.class)
public class IssueDataTest extends AbstractQuickfixTest {

	private static final String PREFIX = "//irrelevant\n\t\t";
	private static final String MODEL_WITH_LINKING_ERROR = PREFIX + QuickfixCrossrefTestLanguageValidator.TRIGGER_VALIDATION_ISSUE + "{}";

	private static boolean WAS_AUTOBUILD;

	@BeforeClass
	public static void beforeClass() throws Exception {
		WAS_AUTOBUILD = IResourcesSetupUtil.setAutobuild(false);
	}

	@AfterClass
	public static void afterClass() throws Exception {
		IResourcesSetupUtil.setAutobuild(WAS_AUTOBUILD);
	}

	@Test public void testIssueData() throws Exception {
		IFile dslFile = dslFile(getProjectName(), getFileName(), getFileExtension(), MODEL_WITH_LINKING_ERROR);
		XtextEditor xtextEditor = openEditor(dslFile);
		IXtextDocument document = xtextEditor.getDocument();
		IResource file = xtextEditor.getResource();
		List<Issue> issues = getAllValidationIssues(document);
		assertEquals(1, issues.size());
		Issue issue = issues.get(0);
		assertEquals(2, issue.getLineNumber().intValue());
		assertEquals(3, issue.getColumn().intValue());
		assertEquals(PREFIX.length(), issue.getOffset().intValue());
		assertEquals(QuickfixCrossrefTestLanguageValidator.TRIGGER_VALIDATION_ISSUE.length(), issue.getLength().intValue());
		String[] expectedIssueData = new String[]{ QuickfixCrossrefTestLanguageValidator.ISSUE_DATA_0,
			QuickfixCrossrefTestLanguageValidator.ISSUE_DATA_1};
		assertTrue(Arrays.equals(expectedIssueData, issue.getData()));
		Thread.sleep(1000);

		IAnnotationModel annotationModel = xtextEditor.getDocumentProvider().getAnnotationModel(
				xtextEditor.getEditorInput());
		AnnotationIssueProcessor annotationIssueProcessor = 
				new AnnotationIssueProcessor(document, annotationModel, new IssueResolutionProvider.NullImpl());
		annotationIssueProcessor.processIssues(issues, new NullProgressMonitor());
		Iterator<?> annotationIterator = annotationModel.getAnnotationIterator();
		// filter QuickDiffAnnotations
		List<Object> allAnnotations = Lists.newArrayList(annotationIterator);
		List<XtextAnnotation> annotations = newArrayList(filter(allAnnotations, XtextAnnotation.class));
		assertEquals(annotations.toString(), 1, annotations.size());
		XtextAnnotation annotation = annotations.get(0);
		assertTrue(Arrays.equals(expectedIssueData, annotation.getIssueData()));
		IssueUtil issueUtil = new IssueUtil();
		Issue issueFromAnnotation = issueUtil.getIssueFromAnnotation(annotation);
		assertTrue(Arrays.equals(expectedIssueData, issueFromAnnotation.getData()));

		new MarkerCreator().createMarker(issue, file, MarkerTypes.FAST_VALIDATION);
		IMarker[] markers = file.findMarkers(MarkerTypes.FAST_VALIDATION, true, IResource.DEPTH_ZERO);
		String errorMessage = new AnnotatedTextToString().withFile(dslFile).withMarkers(markers).toString().trim();
		assertEquals(errorMessage, 1, markers.length);
		String attribute = (String) markers[0].getAttribute(Issue.DATA_KEY);
		assertNotNull(attribute);
		assertTrue(Arrays.equals(expectedIssueData, Strings.unpack(attribute)));

		Issue issueFromMarker = issueUtil.createIssue(markers[0]);
		assertEquals(issue.getColumn(), issueFromMarker.getColumn());
		assertEquals(issue.getLineNumber(), issueFromMarker.getLineNumber());
		assertEquals(issue.getOffset(), issueFromMarker.getOffset());
		assertEquals(issue.getLength(), issueFromMarker.getLength());
		assertTrue(Arrays.equals(expectedIssueData, issueFromMarker.getData()));
	}
}
