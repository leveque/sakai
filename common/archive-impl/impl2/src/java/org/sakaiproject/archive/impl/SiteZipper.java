/***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.archive.impl;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;


public class SiteZipper {
	
	private static Log log = LogFactory.getLog(SiteZipper.class);
	
	protected ServerConfigurationService serverConfigurationService = null;
	public void setServerConfigurationService(ServerConfigurationService service) {
		serverConfigurationService = service;
	}
	
	/**
	 * Unzip a zip file into the unzip directory. Return the NAME of the archive so we can then merge from it. The merger knows the base dir.
	 * @param zipFilePath		path to ZIP file
	 * @param m_unzipPath		unzip dir for zips
	 * @return
	 * @throws IOException
	 */
	public String unzipArchive(String zipFilePath, String m_unzipPath) throws IOException {
		
		log.debug("zipFilePath: " + zipFilePath);
		
		ZipFile zipFile = new ZipFile(zipFilePath);
	    Enumeration<? extends ZipEntry> entries = zipFile.entries();
	    while (entries.hasMoreElements()) {
	        ZipEntry entry = entries.nextElement();
	        
	        //destination file from zip. Straight into the normal archive directory
	        File dest = new File(m_unzipPath, entry.getName());
	        log.debug("Dest: " + dest.getAbsolutePath());

	        if(entry.isDirectory()) {
	        	//create dir
	        	dest.mkdir();
	        } else {
	        	//extract contents
		        InputStream in = zipFile.getInputStream(entry);
		        OutputStream out = new FileOutputStream(dest);
		        IOUtils.copy(in, out);
		        IOUtils.closeQuietly(in);
		        IOUtils.closeQuietly(out);
	        }
	    }
		
	    //get original filename, remove timestamp, add -archive
	    String unzippedArchivePath = StringUtils.substringAfterLast(StringUtils.substringBeforeLast(zipFile.getName(), "-") + "-archive", File.separator);
	    
	    log.debug("unzippedArchivePath: " + unzippedArchivePath);
	    
		return unzippedArchivePath;
		
	}

	/**
	 * Zip a site archive. It is stored back in the zip directory
	 * @param siteId			site that has already been archived
	 * @param m_storagePath		path to where the archives are
	 * @return
	 * @throws IOException
	 */
	public boolean zipArchive(String siteId, String m_storagePath) throws IOException {
		
		//get path to archive dir for this site
		//suffix of -archive is hardcoded as per archive service
		String archivePath = m_storagePath + siteId + "-archive";
		
		//setup timestamp
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		String timestamp = dateFormat.format(Calendar.getInstance().getTime());
		
		//create path to compressed archive
		String compressedArchivePath = m_storagePath + siteId + "-" + timestamp + ".zip";
		File zipFile = new File(compressedArchivePath);
		
		if(!zipFile.exists()) {
			log.info("Creating zip file: " + compressedArchivePath);
			zipFile.createNewFile();
		}
		
		FileOutputStream fOut = null;
        BufferedOutputStream bOut = null;
        ZipArchiveOutputStream zOut = null;
        
        try {
            fOut = new FileOutputStream(zipFile);
            bOut = new BufferedOutputStream(fOut);
            zOut = new ZipArchiveOutputStream(bOut);
            addFileToZip(zOut, archivePath, ""); //add the directory which will then add all files recursively
        } finally {
            zOut.finish();
            zOut.close();
            bOut.close();
            fOut.close();
        }
		
		//create a sha1 hash of the zip
		String hashPath = m_storagePath + siteId + "-" + timestamp + ".sha1";
		log.info("Creating hash: " + hashPath);
		FileInputStream zip  = new FileInputStream(compressedArchivePath);
		String hash = DigestUtils.sha1Hex(zip);
		FileUtils.writeStringToFile(new File(hashPath), hash);
		
		return true;
	}
	
	/**
     * Creates a zip entry for the path specified with a name built from the base passed in and the file/directory
     * name. If the path is a directory, a recursive call is made such that the full directory is added to the zip.
     *
     * @param zOut The zip file's output stream
     * @param path The filesystem path of the file/directory being added
     * @param base The base prefix to for the name of the zip file entry
     *
     * @throws IOException If anything goes wrong
     */
    private static void addFileToZip(ZipArchiveOutputStream zOut, String path, String base) throws IOException {
        File f = new File(path);
        String entryName = base + f.getName();
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(f, entryName);
 
        zOut.putArchiveEntry(zipEntry);
 
        if (f.isFile()) {
            FileInputStream fInputStream = null;
            try {
                fInputStream = new FileInputStream(f);
                IOUtils.copy(fInputStream, zOut);
                zOut.closeArchiveEntry();
            } finally {
                IOUtils.closeQuietly(fInputStream);
            }
 
        } else {
            zOut.closeArchiveEntry();
            File[] children = f.listFiles();
 
            if (children != null) {
                for (File child : children) {
                    addFileToZip(zOut, child.getAbsolutePath(), entryName + "/");
                }
            }
        }
    }
    
}
