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
import java.io.File;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.variableresolvers.PathVariableResolver;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.preference.IPreferenceStore;

import com.intel.sgx.preferences.PreferenceConstants;
import com.intel.sgx.preferences.SGXPreferencePage;

public class SdkPathVariableProvider extends PathVariableResolver {

	public SdkPathVariableProvider() {
		super();
	}

	@Override
	public String[] getVariableNames(String variable, IResource resource) {
		String variableNames[] = {"SGX_SDK_DIR_PATH"};
		return (variableNames);
	}
	
	@Override
	public String getValue(String variable, IResource resource) {
		if(variable.equals("SGX_SDK_DIR_PATH")) {		
			IPreferenceStore store = Activator.getDefault().getPreferenceStore();
			String SDKPath = store.getString(PreferenceConstants.SDK_PATH);
			IPath SDKCanonicalPath= new Path(SDKPath);
			return(SDKCanonicalPath.append("Include").toOSString());
		}					
		return null;
	}
	
	public static String getSGXSdkLocation() {
        return Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.SDK_PATH);
    }
	
	public static boolean isSGXSdkLocationValid() {
        String location = getSGXSdkLocation();
        if (location.length() == 0)
            return false;

        return isValidSGXSdkLocation(location);
    }

    public static boolean isValidSGXSdkLocation(String location) {
    	File dir = new File(location);
        if (!dir.isDirectory())
            return false;
        
        return new PreferenceConstants.SGXSDK64Descriptor(dir).getSignerPath().canExecute()
        		|| new PreferenceConstants.SGXSDK32Descriptor(dir).getSignerPath().canExecute();
    }

}
