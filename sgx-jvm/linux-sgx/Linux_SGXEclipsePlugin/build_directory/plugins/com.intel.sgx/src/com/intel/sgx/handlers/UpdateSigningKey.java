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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
//import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
//import java.security.KeyPair;
//import java.security.KeyPairGenerator;
//import java.security.NoSuchAlgorithmException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

//import org.bouncycastle.openssl.PEMWriter;
import com.intel.sgx.Activator;
import com.intel.sgx.dialogs.UpdateSignKeyDialog;

public class UpdateSigningKey extends SGXHandler {

	public String sourceKeyFile = null;
	public String destinationKeyFile = null;
	public String projectPath = null;

	@Override
	protected Object executeSGXStuff() throws ErrorException, CancelException {

		UpdateSignKeyDialog dialog = new UpdateSignKeyDialog(shell, this);
		int result = dialog.open();
		if (result != InputDialog.OK) {
			cancel();
		}
		
		if(UpdateSignKeyDialog.regenerate == false)
		{
			IPath sourceFile = Path.fromOSString(sourceKeyFile);
			IPath destFile = Path.fromOSString(destinationKeyFile);
			copyFile(sourceFile.toFile(), destFile.toFile());
			refreshProject();
			info("Update Intel(R) SGX Enclave Signing Key","copied \n'" + sourceKeyFile + "' into \n'" + destFile + "'");
		} else {
			UpdateSignKeyDialog.regenerate = false;
			try {
				Process q;
				String opensslCmd = "openssl genrsa -out " + destinationKeyFile
						+ " -3 3072";
				q = Runtime.getRuntime().exec(opensslCmd);
				BufferedReader stdInput = new BufferedReader(
						new InputStreamReader(q.getInputStream()));
				BufferedReader stdErr = new BufferedReader(
						new InputStreamReader(q.getErrorStream()));
				String s = null;
				while ((s = stdInput.readLine()) != null) {
				}
				while ((s = stdErr.readLine()) != null) {
				}
				project.refreshLocal(IResource.DEPTH_INFINITE, null);
				if (q.exitValue() == 0){
					info("Update Intel(R) SGX Enclave Signing Key","'"+destinationKeyFile+"'"+" was generated!");
				} else {
					quitWithError("Could not generate '"+destinationKeyFile+"'!!!");
				}
			} catch (IOException e) {
				Activator.log(e);
				e.printStackTrace();
			} catch (CoreException e) {
				Activator.log(e);
				e.printStackTrace();
			}
		}
		return null;
	}
}
