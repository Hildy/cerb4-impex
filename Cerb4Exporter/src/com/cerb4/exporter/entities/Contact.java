package com.cerb4.exporter.entities;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.cerb4.exporter.Database;
import com.cerb4.exporter.Driver;
import com.cerb4.impex.Configuration;
import com.cerb4.impex.XMLThread;

public class Contact {
	public void export() {
		Connection conn = Database.getInstance();
		String cfgOutputDir = Configuration.get("outputDir", "output");
		String sExportEncoding = new String(Configuration.get("exportEncoding", "ISO-8859-1"));
		
		Integer iCount = 0;
		Integer iSubDir = 0;
		
		try {
			Statement s = conn.createStatement();
			s.execute("SELECT a.id, a.first_name, a.last_name, a.email, aa.pass, o.name AS org FROM address a LEFT JOIN contact_org o ON (o.id=a.contact_org_id)  LEFT JOIN address_auth aa ON a.id = aa.address_id ORDER BY a.id ASC");
			ResultSet rs = s.getResultSet();
	
			File outputDir = null;
			
			while(rs.next()) {
				
				Document doc = DocumentHelper.createDocument();
				Element eContact = doc.addElement("contact");
				doc.setXMLEncoding(sExportEncoding);
				
				Integer iId = rs.getInt("id");
				String sFirstName = Driver.fixMagicQuotes(rs.getString("first_name"));
				String sLastName = Driver.fixMagicQuotes(rs.getString("last_name"));
				String sEmail = Driver.fixMagicQuotes(rs.getString("email"));
				String sPassword = Driver.fixMagicQuotes(rs.getString("pass"));
				//String sPhone = Driver.fixMagicQuotes(rs.getString("phone"));
				String sOrg = Driver.fixMagicQuotes(rs.getString("org"));
				
				eContact.addElement("first_name").addText(sFirstName);
				eContact.addElement("last_name").addText(sLastName);
				eContact.addElement("email").addText(sEmail);
				eContact.addElement("password").addText(sPassword);
				eContact.addElement("phone").addText("");
				eContact.addElement("organization").addText(sOrg);
				
				if(0 == iCount % 2000 || 0 == iCount) {
					iSubDir++;
					
					// Make the output subdirectory
					outputDir = new File(cfgOutputDir+"/03-contacts-" + String.format("%09d", iId));
					outputDir.mkdirs();
				}
				
				String sXmlFileName = outputDir.getPath() + "/" + String.format("%09d",iId) + ".xml";
				
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
