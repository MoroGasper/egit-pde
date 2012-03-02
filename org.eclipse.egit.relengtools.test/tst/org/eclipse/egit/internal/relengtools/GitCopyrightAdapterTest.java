/*******************************************************************************
 * Copyright (c) 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.egit.internal.relengtools;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GitCopyrightAdapterTest extends LocalDiskRepositoryTestCase {

	private static final IProgressMonitor NULL_MONITOR = new NullProgressMonitor();

	private static final String PROJECT_NAME = "Project";

	private static final String FILE_NAME = "Dummy.java";

	private Repository db;

	private File trash;

	private File gitDir;

	private IProject project;

	private IFile file;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
		trash = db.getWorkTree();
		gitDir = new File(trash, Constants.DOT_GIT);
		project = createProject(PROJECT_NAME);
		file = project.getFile(FILE_NAME);
		connect();
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (project.exists())
			project.delete(true, true, NULL_MONITOR);
		if (gitDir.exists())
			FileUtils.delete(gitDir, FileUtils.RECURSIVE | FileUtils.RETRY);
		super.tearDown();
	}

	@Test
	public void testLastModifiedYear() throws Exception {
		final Git git = new Git(db);
		git.add().addFilepattern(PROJECT_NAME + "/" + FILE_NAME).call();
		final PersonIdent committer2012 = new PersonIdent(committer,
				getDateForYear(2012));
		git.commit().setMessage("initial commit").setCommitter(committer2012)
				.call();

		final GitCopyrightAdapter adapter = new GitCopyrightAdapter(
				new IResource[] { project });
		adapter.initialize(NULL_MONITOR);
		final int lastModifiedYear = adapter.getLastModifiedYear(file,
				NULL_MONITOR);

		assertEquals(2012, lastModifiedYear);
	}

	@Test
	public void testCopyrightUpdateComment() throws Exception {
		final Git git = new Git(db);
		git.add().addFilepattern(PROJECT_NAME + "/" + FILE_NAME).call();
		git.commit().setMessage("copyright update").call();

		final GitCopyrightAdapter adapter = new GitCopyrightAdapter(
				new IResource[] { project });
		adapter.initialize(NULL_MONITOR);
		final int lastModifiedYear = adapter.getLastModifiedYear(file,
				NULL_MONITOR);

		assertEquals(0, lastModifiedYear);
	}

	private IProject createProject(String name) throws Exception {
		final IProject project = ResourcesPlugin.getWorkspace().getRoot()
				.getProject(name);
		if (project.exists())
			project.delete(true, null);
		final IProjectDescription desc = ResourcesPlugin.getWorkspace()
				.newProjectDescription(name);
		desc.setLocation(new Path(new File(db.getWorkTree(), name).getPath()));
		project.create(desc, null);
		project.open(null);

		final IFile file = project.getFile(FILE_NAME);
		file.create(
				new ByteArrayInputStream("Hello, world".getBytes(project
						.getDefaultCharset())), false, null);
		return project;
	}

	private void connect() throws CoreException {
		new ConnectProviderOperation(project, gitDir).execute(null);
	}

	private Date getDateForYear(int year) throws ParseException {
		final SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
		return formatter.parse(Integer.toString(year) + "/6/30");
	}

}
