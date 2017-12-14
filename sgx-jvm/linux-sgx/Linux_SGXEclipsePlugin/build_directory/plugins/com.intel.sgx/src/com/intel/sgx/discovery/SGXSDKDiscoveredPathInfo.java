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


package com.intel.sgx.discovery;

import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.make.core.scannerconfig.IDiscoveredPathManager.IDiscoveredPathInfo;
import org.eclipse.cdt.make.core.scannerconfig.IDiscoveredPathManager.IDiscoveredScannerInfoSerializable;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import com.intel.sgx.Activator;
/*
 * This code has been taken from the NDK plugin for Linux. If there is an update to this code there, then refactor this code.
 */
public class SGXSDKDiscoveredPathInfo implements IDiscoveredPathInfo {

	private final IProject mProject;
    private long mLastUpdate = IFile.NULL_STAMP;
    private IPath[] mIncludePaths;
    private Map<String, String> mSymbols;
    private boolean mNeedReindexing = false;
    private static final IPath LINUX_MK = new Path("sgx/Makefile");
    
    //Keys for preferences
    // public static final String LAST_UPDATE = "lastUpdate"; //$NON-NLS-1$

    
    public SGXSDKDiscoveredPathInfo(IProject project) {
        this.mProject = project;
        load();
    }

	@Override
	public IProject getProject() {
		return mProject;
	}

	@Override
	public IPath[] getIncludePaths() {
		if (mNeedReindexing) {
            CCorePlugin.getIndexManager().reindex(CoreModel.getDefault().create(mProject));
            mNeedReindexing = false;
        }
		return mIncludePaths;
	}

    void setIncludePaths(List<String> pathStrings) {
        mIncludePaths = new IPath[pathStrings.size()];
        int i = 0;
        for (String path : pathStrings)
            mIncludePaths[i++] = new Path(path);
        mNeedReindexing = true;
    }

	@Override
	public Map<String, String> getSymbols() {
		if (mSymbols == null)
            mSymbols = new HashMap<String, String>();
        return mSymbols;
    }

    void setSymbols(Map<String, String> symbols) {
        this.mSymbols = symbols;
    }

	@Override
	public IDiscoveredScannerInfoSerializable getSerializable() {
		return null;
	}

	public void update(IProgressMonitor monitor) throws CoreException {
        if (!needUpdating())
            return;

        new SGXSDKDiscoveryUpdater(this).runUpdate(monitor);

        if (mIncludePaths != null && mSymbols != null) {
            recordUpdate();
            save();
        }
    }

    private boolean needUpdating() {

    	
        if (mLastUpdate == IFile.NULL_STAMP) {
        	    return true;
        }

        return mProject.getFile(LINUX_MK).getLocalTimeStamp() > mLastUpdate;
    }

    private void recordUpdate() {
        mLastUpdate = mProject.getFile(LINUX_MK).getLocalTimeStamp();
    }

    public void delete() {
        mLastUpdate = IFile.NULL_STAMP;
    }

    private File getInfoFile() {
        File stateLoc = Activator.getDefault().getStateLocation().toFile();
        return new File(stateLoc, mProject.getName() + ".pathInfo"); //$NON-NLS-1$
    }
    
    private void save() {
        try {
            File infoFile = getInfoFile();
            infoFile.getParentFile().mkdirs();
            PrintStream out = new PrintStream(infoFile);

            out.print("t,"); //$NON-NLS-1$
            out.print(mLastUpdate);
            out.println();

            for (IPath include : mIncludePaths) {
                out.print("i,"); //$NON-NLS-1$
                out.print(include.toPortableString());
                out.println();
            }

            for (Entry<String, String> symbol : mSymbols.entrySet()) {
                out.print("d,"); //$NON-NLS-1$
                out.print(symbol.getKey());
                out.print(","); //$NON-NLS-1$
                out.print(symbol.getValue());
                out.println();
            }

            out.close();
        } catch (IOException e) {
            Activator.log(e);
        }

    }
    
	private void load() {
        try {
            File infoFile = getInfoFile();
            if (!infoFile.exists())
                return;

            long timestamp = IFile.NULL_STAMP;
            List<IPath> includes = new ArrayList<IPath>();
            Map<String, String> defines = new HashMap<String, String>();

            BufferedReader reader = new BufferedReader(new FileReader(infoFile));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                switch (line.charAt(0)) {
                    case 't':
                        timestamp = Long.valueOf(line.substring(2));
                        break;
                    case 'i':
                        includes.add(Path.fromPortableString(line.substring(2)));
                        break;
                    case 'd':
                        int n = line.indexOf(',', 2);
                        if (n == -1)
                            defines.put(line.substring(2), ""); //$NON-NLS-1$
                        else
                            defines.put(line.substring(2, n), line.substring(n + 1));
                        break;
                }
            }
            reader.close();

            mLastUpdate = timestamp;
            mIncludePaths = includes.toArray(new IPath[includes.size()]);
            mSymbols = defines;
        } catch (IOException e) {
            Activator.log(e);
        }
    }

}
