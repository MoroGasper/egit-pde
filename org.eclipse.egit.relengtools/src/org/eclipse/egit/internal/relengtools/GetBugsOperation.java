/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.egit.internal.relengtools;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.egit.core.GitTag;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.statushandlers.StatusManager;

public class GetBugsOperation {
	private static final String BUG_DATABASE_PREFIX = "https://bugs.eclipse.org/bugs/show_bug.cgi?id=";

	private static final String BUG_DATABASE_POSTFIX = "&ctype=xml";

	private static final String SUM_OPEN_TAG = "<short_desc>";

	private static final String SUM_CLOSE_TAG = "</short_desc>";

	private static final String STATUS_OPEN_TAG = "<bug_status>";

	private static final String STATUS_CLOSE_TAG = "</bug_status";

	private static final String RES_OPEN_TAG = "<resolution>";

	private static final String RES_CLOSE_TAG = "</resolution>";

	private static final String RESOLVED = "RESOLVED";

	private static final String VERIFIED = "VERIFIED";

	private final ReleaseWizard wizard;

	private final Pattern bugPattern;

	protected GetBugsOperation(ReleaseWizard wizard, Object syncInfoSet) {
		this.wizard = wizard;
		bugPattern = Pattern.compile("bug (\\d+)", Pattern.CASE_INSENSITIVE
				| Pattern.UNICODE_CASE);
	}

	protected void run(final BuildNotesPage page) {
		try {
			wizard.getContainer().run(true, true, new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) {
					final int totalWork = 101;
					monitor.beginTask(
							Messages.getString("GetBugsOperation.0"), totalWork); //$NON-NLS-1$

					final IProject[] selectedProjects = wizard
							.getSelectedProjects();

					// task 1 -- get bug numbers from comments
					Set<Integer> bugTree;
					try {
						bugTree = getBugNumbersFromComments(
								selectedProjects,
								new SubProgressMonitor(
										monitor,
										85,
										SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
					} catch (final Exception e) {
						monitor.done();
						return;
					}

					// task 2 -- create map of bugs and summaries
					final Integer[] bugs = bugTree.toArray(new Integer[0]);
					final Map<Integer, String> map = getBugzillaSummaries(bugs,
							new SubProgressMonitor(monitor, 15,
									SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
					page.getShell().getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							page.setMap(map);
						}
					});
					monitor.done();
				}
			});
		} catch (final InterruptedException e) {
			e.printStackTrace();
		} catch (final InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	protected Set<Integer> getBugNumbersFromComments(IProject[] projects,
			IProgressMonitor monitor) throws Exception {
		monitor.beginTask("Scanning comments for bug numbers", projects.length);
		final Set<Integer> set = new TreeSet<Integer>();
		for (int i = 0; i < projects.length; i++) {
			getBugNumbersForProject(projects[i], monitor, set);
			monitor.worked(1);
		}
		monitor.done();
		return set;
	}

	private void getBugNumbersForProject(IProject project,
			IProgressMonitor monitor, Set<Integer> set) throws Exception {

		final RepositoryMapping rm = RepositoryMapping.getMapping(project);
		final Repository repository = rm.getRepository();

		final RevCommit previousCommit = ShowInfoHandler.getCommitForTag(
				repository, getProjectTag(project).getName());
		final RevCommit latestCommit = ShowInfoHandler.getLatestCommitFor(rm,
				repository, project);

		final Git git = new Git(repository);
		final LogCommand log = git.log();
		log.addRange(previousCommit, latestCommit);
		for (final RevCommit commit : log.call()) {
			findBugNumber(commit.getFullMessage(), set);
		}
	}

	private GitTag getProjectTag(IProject project) {
		final MapEntry mapEntry = wizard.getMapProject().getMapEntry(project);
		if (mapEntry != null)
			return mapEntry.getTag();
		return MapEntry.DEFAULT;
	}

	protected void findBugNumber(String comment, Set<Integer> set) {
		if (comment == null) {
			return;
		}
		final Matcher matcher = bugPattern.matcher(comment);
		while (matcher.find()) {
			final Integer bugNumber = new Integer(matcher.group(1));
			set.add(bugNumber);
		}
	}

	/*
	 * Method uses set of bug numbers to query bugzilla and get summary of each
	 * bug
	 */
	protected Map<Integer, String> getBugzillaSummaries(Integer[] bugs,
			IProgressMonitor monitor) {
		monitor.beginTask(
				Messages.getString("GetBugsOperation.1"), bugs.length + 1); //$NON-NLS-1$
		HttpURLConnection hURL;
		DataInputStream in;
		URLConnection url;
		StringBuffer buffer;
		final TreeMap<Integer, String> map = new TreeMap<Integer, String>();
		for (int i = 0; i < bugs.length; i++) {
			try {
				url = (new URL(BUG_DATABASE_PREFIX + bugs[i]
						+ BUG_DATABASE_POSTFIX).openConnection());
				if (url instanceof HttpURLConnection) {
					hURL = (HttpURLConnection) url;
					hURL.setAllowUserInteraction(true);
					hURL.connect();
					in = new DataInputStream(hURL.getInputStream());
					buffer = new StringBuffer();
					try {
						if (hURL.getResponseCode() != HttpURLConnection.HTTP_OK) {
							throw new IOException("Bad response code");
						}
						while (true) {
							buffer.append((char) in.readUnsignedByte());
						}
					} catch (final EOFException e) {
						hURL.disconnect();
					}
					final String webPage = buffer.toString();
					final int summaryStartIndex = webPage.indexOf(SUM_OPEN_TAG);
					final int summaryEndIndex = webPage.indexOf(SUM_CLOSE_TAG,
							summaryStartIndex);
					if (summaryStartIndex != -1 & summaryEndIndex != -1) {
						String summary = webPage.substring(summaryStartIndex
								+ SUM_OPEN_TAG.length(), summaryEndIndex);
						summary = summary.replaceAll("&quot;", "\"");
						summary = summary.replaceAll("&lt;", "<");
						summary = summary.replaceAll("&gt;", ">");
						summary = summary.replaceAll("&amp;", "&");
						summary = summary.replaceAll("&apos;", "'");
						final int statusStartIndex = webPage
								.indexOf(STATUS_OPEN_TAG);
						final int statusEndIndex = webPage
								.indexOf(STATUS_CLOSE_TAG);
						if (statusStartIndex != -1 && statusEndIndex != -1) {
							final String bugStatus = webPage
									.substring(statusStartIndex
											+ STATUS_OPEN_TAG.length(),
											statusEndIndex);
							if (bugStatus.equalsIgnoreCase(RESOLVED)
									|| bugStatus.equalsIgnoreCase(VERIFIED)) {
								final int resStartIndex = webPage
										.indexOf(RES_OPEN_TAG);
								final int resEndIndex = webPage
										.indexOf(RES_CLOSE_TAG);
								if (resStartIndex != -1 && resEndIndex != -1) {
									final String resolution = webPage
											.substring(resStartIndex
													+ RES_OPEN_TAG.length(),
													resEndIndex);
									if (resolution != null
											&& !resolution.equals("")) {
										summary = summary + " (" + resolution
												+ ")";
									}
								}
							} else {
								summary = summary + " (" + bugStatus + ")";
							}
						}
						map.put(bugs[i], summary);
					}
				}
			} catch (final IOException e) {
				StatusManager
						.getManager()
						.handle(new Status(IStatus.ERROR, "id", Messages
								.getString("GetBugsOperation.Error"), e),
								StatusManager.SHOW | StatusManager.LOG);
			}
			monitor.worked(1);
		}
		monitor.done();
		return map;
	}
}
