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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;

public class ModuleCreationBaseHandler {
	public boolean isCPProject(IProject project)
	{
			boolean isCPProject = false;
			IProjectDescription description;
			try {
				description = project.getDescription();
				String[] natures = description.getNatureIds();
				for(String nature: natures){
					if(nature.equals("org.eclipse.cdt.core.ccnature"))
						isCPProject = true;
				}
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return isCPProject;
		
	}
}
