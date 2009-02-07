package com.cerberusweb.cerb2.entities;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.cerb4.impex.Configuration;
import com.cerb4.impex.XMLThread;
import com.cerberusweb.cerb2.Database;
import com.cerberusweb.cerb2.Driver;

public class Worker {
	public void export() {
		Connection conn = Database.getInstance();
		String cfgOutputDir = Configuration.get("outputDir", "output");
		String sExportEncoding = new String(Configuration.get("exportEncoding", "ISO-8859-1"));
		
		Integer iCount = 0;
		Integer iSubDir = 0;
		
		try {
			Statement s = conn.createStatement();
			s.execute("SELECT user_id, user_name, user_email, user_password, user_superuser FROM user ORDER BY user_id ASC");
			ResultSet rs = s.getResultSet();
	
			File outputDir = null;
			
			while(rs.next()) {
				
				if(0 == iCount % 2000 || 0 == iCount) {
					iSubDir++;
					
					// Make the output subdirectory
					outputDir = new File(cfgOutputDir+"/00-workers-" + String.format("%06d", iSubDir));
					outputDir.mkdirs();
				}
				
				Document doc = DocumentHelper.createDocument();
				Element eWorker = doc.addElement("worker");
				doc.setXMLEncoding(sExportEncoding);
				
				Integer iId = rs.getInt("user_id");
				String sName = Driver.fixMagicQuotes(rs.getString("user_name"));
				String sEmail = Driver.fixMagicQuotes(rs.getString("user_email"));
				String sPassword = rs.getString("user_password");
				Integer isSuperuser = rs.getInt("user_superuser");

				if(0 == sEmail.length())
					continue;
				
				// Split name
				String sFirstName = "Worker";
				String sLastName = "";
				
				if(-1 != sName.indexOf(" ")) {
					sFirstName = sName.substring(0,sName.indexOf(" "));
					sLastName = sName.substring(sName.indexOf(" "));
				} else {
					sFirstName = sName;
				}
				
				eWorker.addElement("first_name").addText(sFirstName);
				eWorker.addElement("last_name").addText(sLastName);
				eWorker.addElement("email").addText(sEmail);
				eWorker.addElement("password").addText(sPassword);
				eWorker.addElement("is_superuser").addText(isSuperuser.toString());
				
				String sXmlFileName = outputDir.getPath() + "/" + String.format("%06d", iId) + ".xml";

				try {
					new XMLThread(doc, sXmlFileName).start();
				} catch(Exception e) {
					e.printStackTrace();
				}
				
				iCount++;
			}
			
			rs.close();
			s.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
