///////////////////////////////////////////////////////////////////////////
// Copyright (c) 2016 Intel Corporation.				 //
// 									 //
// All rights reserved. This program and the accompanying materials	 //
// are made available under the terms of the Eclipse Public License v1.0 //
// which accompanies this distribution, and is available at		 //
// http://www.eclipse.org/legal/epl-v10.html				 //
// 									 //
// Contributors:							 //
//     Intel Corporation - initial implementation and documentation	 //
///////////////////////////////////////////////////////////////////////////


package com.intel.sgx.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Utility base class for all Handlers The derived classes must implement
 * executeSgxStuff() instead of execute(). The user may call methods cancel(),
 * quitWithError() & info()
 * 
 * @author mlutescu
 *
 */
public abstract class SGXHandler implements IHandler {

	public String projectPath = null;
	protected IProject project;
	protected Shell shell;

	/**
	 * Throwing this IS an error. Means that the process can't continue
	 * 
	 * @author mlutescu
	 *
	 */
	static protected class ErrorException extends Exception {

		public ErrorException(String message) {
			super(message);
		}

	}

	/**
	 * Throwing this is not an error; just signals stop of execution because the
	 * user cancels
	 * 
	 * @author mlutescu
	 *
	 */
	static protected class CancelException extends Exception {

		public CancelException() {
			super();
		}

	}

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			initializeShell();
			initializeProject(event);
			return executeSGXStuff();
		} catch (ErrorException e) {
			e.printStackTrace();
		} catch (CancelException e) {
			// do nothing by design ; it's Ok to not handle this exception.
		}
		return null;
	}

	protected abstract Object executeSGXStuff() throws ErrorException,
			CancelException;

	public SGXHandler() {
		super();
	}

	public static void copyFile(File source, File dest) throws ErrorException {
		byte[] bytes = new byte[4092];
		if (source != null && dest != null) {
			if (source.isFile()) {
				FileInputStream in = null;
				FileOutputStream out = null;
				try {
					in = new FileInputStream(source);
					out = new FileOutputStream(dest);
					int len;
					while ((len = in.read(bytes)) != -1) {
						out.write(bytes, 0, len);
					}
				} catch (IOException e) {
					System.err.println("Error: " + e.toString());
					quitWithError("Could not copy from\n" + "'"
							+ source.getAbsolutePath() + "'\n" + "to\n" + "'"
							+ dest.getAbsolutePath());
				} finally {
					try {
						if (in != null) {
							in.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (out != null) {
							try {
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
	}

	protected static void quitWithError(String message) throws ErrorException {
		JOptionPane.showMessageDialog(null, message, "Two Step Enclave Sign",
				JOptionPane.ERROR_MESSAGE);
		throw new ErrorException(message);
	}

	static protected void cancel() throws CancelException {
		throw new CancelException();
	}

	protected void initializeProject(ExecutionEvent event)
			throws ErrorException {
		project = null;
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		Object element = null;
		if (selection instanceof IStructuredSelection) {
			element = ((IStructuredSelection) selection).getFirstElement();
			if (element instanceof IResource) {
				project = ((IResource) element).getProject();
			}
		}

		if (!project.exists()) {
			quitWithError("Project not found");
		}

	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener arg0) {
	}

	@Override
	public void addHandlerListener(IHandlerListener arg0) {
	}

	@Override
	public void dispose() {
	}

	protected void initializeShell() {
		shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
	}

	protected void refreshProject() throws ErrorException {
		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (CoreException e1) {
			quitWithError(e1.getLocalizedMessage());
		}
	}
	
	protected void info(String windowName, String message) {
		JOptionPane.showMessageDialog(null, message,windowName,
				JOptionPane.INFORMATION_MESSAGE);
	}

}
