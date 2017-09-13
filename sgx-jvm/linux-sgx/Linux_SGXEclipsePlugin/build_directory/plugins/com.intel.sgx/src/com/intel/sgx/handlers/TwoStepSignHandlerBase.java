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
import java.io.IOException;
import java.io.InputStreamReader;

import com.intel.sgx.preferences.PreferenceConstants;

public abstract class TwoStepSignHandlerBase extends SGXHandler {

	public String hashFile = null;
	public String configFile = null;
	public String enclaveFile = null;
	public String externalSignPublicKeyFile = null;
	public String externallySignedHashFile = null;
	public String outputSignedEnclaveFile = null;

	protected File signtool;
	
	public TwoStepSignHandlerBase() {
		super();
	}

	protected void executeGenData() throws ErrorException {
		validateConfigFile();
		validateEnclaveFile();

		refreshProject();
		executeSignTool(new String[] { "gendata", 
				"-enclave", enclaveFile,
				"-config", configFile, 
				"-out", hashFile });
		refreshProject();
		
		validateHashFile();

	}

	protected void executeCatSig() throws ErrorException {
		validateEnclaveFile();
		validateConfigFile();
		validateHashFile();

		validateExternalSignPublicKeyFile();
		validateExternallySignedHashFile();
		
		executeSignTool("catsig", 
				// enclave data:
				"-enclave", enclaveFile, 
				"-config",	configFile,
				// previously generated:
				"-unsigned", hashFile,
				// externally generated
				"-key", externalSignPublicKeyFile,
				"-sig", externallySignedHashFile, 
				// output
				"-out", outputSignedEnclaveFile 

				);
		
		refreshProject();
		
		validateOutputSignedEnclaveFile();
		
		info("Two Step Enclave Sign","Enclave signed successfully !");
	}

	void initializeSigntool() throws ErrorException {
		signtool = PreferenceConstants.getSDKDescriptor().getSignerPath();
		if (!signtool.exists() || signtool.isDirectory()) {
			quitWithError("Error generating hash! Sign Tool Not Found !\n Please make sure to have written in the box the value for  Intel(R) SGX SDK Directory in Window->Preferences->Intel(R) SGX Preferences. \n Usually the path is in /opt/intel/sgxsdk/" );
		}

	}

	protected void validateEnclaveFile() throws ErrorException {
		File enclave = new File(enclaveFile);
		if (!enclave.exists() || enclave.isDirectory()) {
			quitWithError("Error generating hash! Unsigned Enclave File Not Found! Try building the enclave first");
		}
	}

	protected void validateConfigFile() throws ErrorException {
		if (configFile == null || configFile.isEmpty()) {
			quitWithError("Error Enclave Configuration  File Not Found !");
		}
		File config = new File(configFile);
		if (!config.exists() || config.isDirectory()) {
			quitWithError("Enclave Config File Not Found !");
		}
	}

	protected void validateExternallySignedHashFile() throws ErrorException {
		if (externallySignedHashFile == null || externallySignedHashFile.isEmpty()) {
			quitWithError("Error signing enclave! Signature File Not Found !");
		}
	
		File signature = new File(externallySignedHashFile);
		if (!signature.exists() || signature.isDirectory()) {
			quitWithError("Error signing enclave! Signature File Not Found !");
		}
	}

	protected void validateExternalSignPublicKeyFile() throws ErrorException {
		if (externalSignPublicKeyFile == null || externalSignPublicKeyFile.isEmpty()) {
			quitWithError("Public Key File Not Found !");
		}
	
		File publickkey = new File(externalSignPublicKeyFile);
		if (!publickkey.exists() || publickkey.isDirectory()) {
			quitWithError("Error signing enclave! Public Key File Not Found !");
		}
	}

	private void validateOutputSignedEnclaveFile() throws ErrorException {
		if(outputSignedEnclaveFile == null || outputSignedEnclaveFile.isEmpty())
		{
			quitWithError("Output Signed File Not Found !");
		}
		File outputSignedEnclave = new File(outputSignedEnclaveFile);
		if(!outputSignedEnclave.exists() || outputSignedEnclave.isDirectory())
		{
			quitWithError("Output Signed File Not Found !");
		}

		// TODO Auto-generated method stub
		
	}

	protected void validateHashFile() throws ErrorException {
		if(hashFile == null || hashFile.isEmpty())
		{
			quitWithError("Hash File Not Found !");
		}
		File hash = new File(hashFile);
		if(!hash.exists() || hash.isDirectory())
		{
			quitWithError("Hash File Not Found !");
		}
	}
	
	
	protected void executeSignTool(String... args) throws ErrorException {
		
		Process q;
		try {
			
			String[] allArgs = new String[args.length+1];
			allArgs[0] = signtool.getAbsolutePath();
			System.arraycopy(args,  0, allArgs, 1, args.length);
			
			for (String arg : args){
			}
			String fullOutput = "";
			q = Runtime.getRuntime().exec(allArgs);
	
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(
					q.getInputStream()));
			BufferedReader stdErr = new BufferedReader(new InputStreamReader(
					q.getErrorStream()));
			String s = null;
			while ((s = stdInput.readLine()) != null) {
			}
			String[] out = new String[20];
			int i = 0;
			while ((out[i] = stdErr.readLine()) != null) {
				fullOutput += out[i]+"\n";
				i++;
			}
			String result = out[i - 1];
	
			if (!result.equals("Succeed.")) {
				// quitWithError("Error generating hash! " + out[i - 2]);
				quitWithError("Error generating hash! " + fullOutput);
			}
		} catch (IOException e) {
			quitWithError(e.getLocalizedMessage());
		}
	
	}

}
