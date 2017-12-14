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

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.intel.sgx.Activator;
import com.intel.sgx.dialogs.RemoveEnclaveFileDialog;

public class RemoveEnclave implements IHandler {

	public String edlFilename = "";
	private IPath edlCanonicalFilename;
	
	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String edlBasename;
		IProject project = null;

		// Display display = Display.getCurrent();
		// Shell shell = new Shell(display);
	
		RemoveEnclaveFileDialog dialog  = new RemoveEnclaveFileDialog(null, this);

		if (dialog.open() != InputDialog.OK) {
			return null;
		}

		edlCanonicalFilename = Path.fromOSString(edlFilename);
		edlBasename = edlCanonicalFilename.lastSegment();

		if(edlBasename.isEmpty()){
			return null;
		}
		
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		Object element = null;
		if(selection instanceof IStructuredSelection) {
			element = ((IStructuredSelection)selection).getFirstElement();
			if (element instanceof IResource) {
				project= ((IResource)element).getProject();
			}
		}
		if (!project.exists()) {
			System.err.println("Error: Project not found");
			return null;
		}
			
		IPath targetRelPath = project.getProjectRelativePath().append("sgx").append("enclave_" + edlBasename);

		try {
			for (int i=1;i<=targetRelPath.segmentCount();i++) {
				IFolder subfolder = project.getFolder(targetRelPath.uptoSegment(i));

				if (subfolder.exists()){
					if(subfolder.getProjectRelativePath().toOSString().contains("enclave_"+edlBasename)){
						subfolder.delete(true, true, null);
						break;
					}
				}
			}
			targetRelPath = project.getProjectRelativePath().append("sgx").append(edlBasename);
			for (int i=1;i<=targetRelPath.segmentCount();i++) {
				IFolder subfolder = project.getFolder(targetRelPath.uptoSegment(i));

				if (subfolder.exists()){
					if(subfolder.getProjectRelativePath().toOSString().contains(edlBasename)){
						subfolder.delete(true, true, null);
						break;
					}
				}
			}			
		} catch (Exception e) {
			Activator.log(e);
		}
		
		try {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		} catch (CoreException e) {
			Activator.log(e);
		} catch (IllegalArgumentException e){
			Activator.log(e);
		}
		
		return null;
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
	public void removeHandlerListener(IHandlerListener handlerListener) {
	}

	public static void copyFile(File source, File dest) throws IOException {
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
				} catch (Exception e) {
					System.err.println("Error: " + e.toString());
					Activator.log(e);
				} finally {
					try {
						if (in != null)
							in.close();
					} finally {
						if (out != null)
							out.close();
					}
				}
			}
		}
	}
}
