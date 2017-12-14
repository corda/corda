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


/**
 * This class follows the same solution provided in the NDK_PLUGIN for NDKCommandLauncher.
 */
package com.intel.sgx.build;

import org.eclipse.cdt.core.CommandLauncher;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.intel.sgx.SdkPathVariableProvider;
/**
 * This launches the sgx-ndk-build.
 */
public class SGXSDKCommandLauncher extends CommandLauncher {

    @Override
    public Process execute(IPath commandPath, String[] args, String[] env, IPath changeToDirectory,
            IProgressMonitor monitor)
            throws CoreException {
    	
        return super.execute(commandPath, args, env, changeToDirectory, monitor);
    }
}
