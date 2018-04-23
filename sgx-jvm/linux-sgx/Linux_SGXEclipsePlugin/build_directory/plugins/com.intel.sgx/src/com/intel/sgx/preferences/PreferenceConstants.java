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


package com.intel.sgx.preferences;

import java.io.File;

import org.eclipse.jface.preference.IPreferenceStore;

import com.intel.sgx.Activator;

/**
 * Constant definitions for plug-in preferences
 */
public class PreferenceConstants {

	public static final String SDK_PATH = "SDKPathPreference";
	
	public static ISDKDescriptor getSDKDescriptor() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		File sdkDir = new File(store.getString(PreferenceConstants.SDK_PATH));

		if (System.getProperty("os.arch").contains("64")) {
			return new SGXSDK64Descriptor(sdkDir);
		} else {
			return new SGXSDK32Descriptor(sdkDir);
		}
	}
	
	
	static public class SGXSDK32Descriptor implements ISDKDescriptor {
		private final File sdkDir;
		private final File toolDir;
		private final File signerPath;
		private final File edger8rPath;

		public SGXSDK32Descriptor(File location){
			this.sdkDir = location;
			this.toolDir = new File(location, "bin/x86");
			this.signerPath = new File(toolDir, "sgx_sign");
			this.edger8rPath = new File(toolDir, "sgx_edger8r");
		}
		
		@Override
		public File getSdkDir() {
			return sdkDir;
		}

		@Override
		public File getToolsDir() {
			return toolDir;
		}

		@Override
		public File getSignerPath() {
			return signerPath;
		}

		@Override
		public File getEdger8rPath() {
			return edger8rPath;
		}

	}
	

	static public class SGXSDK64Descriptor implements ISDKDescriptor {
		private final File sdkDir;
		private final File toolDir;
		private final File signerPath;
		private final File edger8rPath;

		public SGXSDK64Descriptor(File sdkDir){
			this.sdkDir = sdkDir;
			this.toolDir = new File(sdkDir, "bin/x64");
			this.signerPath = new File(toolDir, "sgx_sign");
			this.edger8rPath = new File(toolDir, "sgx_edger8r");
		}
		
		@Override
		public File getSdkDir() {
			return sdkDir;
		}

		@Override
		public File getToolsDir() {
			return toolDir;
		}

		@Override
		public File getSignerPath() {
			return signerPath;
		}

		@Override
		public File getEdger8rPath() {
			return edger8rPath;
		}

	}

}
