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


package com.intel.sgx;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

	private static final String BUNDLE_NAME = "com.intel.sgx.messages";//$NON-NLS-1$
	
	/*
	 * TODO - These Strings can be used all through the package to control what messages are displayed.
	 * 		  Todo here is to identify any message that needs to be made configurable.   
	 */
	public static String CreateNativeFolders_No_folders;
	public static String CreateNativeFolders_Missing_project_name;
	public static String CreateNativeFolders_Project_does_not_exist;
	
	static{
		//Bundle initialization.
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}
	
	private Messages(){
	}
}
