package com.cerb4.impex.exporters;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import com.cerb4.impex.Database;

public class Ticket {
	public void export() {
		Connection conn = Database.getInstance();
		String importGroupName = "Import:Cerb3"; // [TODO] Make this configurable
		
		SimpleDateFormat rfcDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");

		Integer iCount = 0;
		Integer iSubDir = 0;
		
		try {
			// [TODO] Skip spam training positives too
			ResultSet rsTickets = conn.createStatement().executeQuery("SELECT t.ticket_id, t.ticket_subject, t.ticket_mask, UNIX_TIMESTAMP(t.ticket_date) as ticket_date, "+
				"UNIX_TIMESTAMP(ticket_last_date) as ticket_updated, t.is_waiting_on_customer, t.is_closed, q.queue_name, q.queue_reply_to "+
				"FROM ticket t "+
				"INNER JOIN queue q ON (q.queue_id=t.ticket_queue_id) "+
				"WHERE t.is_deleted = 0 "+ //  AND t.ticket_id=1473 // AND t.ticket_id=29
				"ORDER BY t.ticket_id ASC "+
				"LIMIT 0,5");
	
			File outputDir = null;
			
			while(rsTickets.next()) {
				
				if(0 == iCount % 2000 || 0 == iCount) {
					iSubDir++;
					
					// Make the output subdirectory
					outputDir = new File("output/02-tickets-" + String.format("%06d", iSubDir));
					outputDir.mkdirs();
				}
				
				Document doc = DocumentHelper.createDocument();
				Element eTicket = doc.addElement("ticket");
				doc.setXMLEncoding("ISO-8859-1");
				
				Integer iTicketId = rsTickets.getInt("ticket_id");
				String sSubject = rsTickets.getString("ticket_subject");
				String sMask = rsTickets.getString("ticket_mask");
				Integer iCreatedDate = rsTickets.getInt("ticket_date");
				Integer iUpdatedDate = rsTickets.getInt("ticket_updated");
				Integer isWaiting = rsTickets.getInt("is_waiting_on_customer");
				Integer isClosed = rsTickets.getInt("is_closed");
				String sQueueName = rsTickets.getString("queue_name");
				String sQueueReplyTo = rsTickets.getString("queue_reply_to");
				
				if(sMask.isEmpty()) {
					// [TODO] Make this prefix configurable
					sMask = String.format("CERB3-%06d", iTicketId);
				}
				
				eTicket.addElement("subject").addText(sSubject);
				eTicket.addElement("group").addText(importGroupName);
				eTicket.addElement("bucket").addText(sQueueName);
				eTicket.addElement("mask").addText(sMask);
				eTicket.addElement("created_date").addText(iCreatedDate.toString());
				eTicket.addElement("updated_date").addText(iUpdatedDate.toString());
				eTicket.addElement("is_waiting").addText(isWaiting.toString());
				eTicket.addElement("is_closed").addText(isClosed.toString());
				
				ResultSet rsRequesters = conn.createStatement().executeQuery("SELECT a.address_address "+
					"FROM requestor r "+
					"INNER JOIN address a ON (a.address_id=r.address_id) "+
					"WHERE r.ticket_id = " + iTicketId + " " 
					);
				
				Element eRequesters = eTicket.addElement("requesters");
				
				while(rsRequesters.next()) {
					String sRequesterAddy = rsRequesters.getString("address_address");
					eRequesters.addElement("address").setText(sRequesterAddy);
				}
				
				ResultSet rsMessages = conn.createStatement().executeQuery("SELECT thread_id, thread_message_id, thread_subject, thread_address_id, address.address_address as sender_from, "+
					"UNIX_TIMESTAMP(thread_date) as thread_date, is_agent_message "+
					"FROM thread "+
					"INNER JOIN address ON (thread.thread_address_id=address.address_id) "+
					"WHERE ticket_id = "  + iTicketId + " " +
					"ORDER BY thread_id ASC");
				
				Element eMessages = eTicket.addElement("messages");
				
				while(rsMessages.next()) {
					Integer iThreadId = rsMessages.getInt("thread_id");
					String sThreadSender = rsMessages.getString("sender_from");
					String sThreadSubject = rsMessages.getString("thread_subject");
					String sThreadMsgId = rsMessages.getString("thread_message_id");
					Long lThreadDate = rsMessages.getLong("thread_date");
					
					Element eMessage = eMessages.addElement("message");
					
					Element eMessageHeaders = eMessage.addElement("headers");
					
					String sMessageDate = rfcDateFormat.format(new Date(lThreadDate*1000));
					
					eMessageHeaders.addElement("date").addText(sMessageDate);
					eMessageHeaders.addElement("to").addText(sQueueReplyTo);
					eMessageHeaders.addElement("from").addText(sThreadSender);
					if(!sThreadSubject.isEmpty())
						eMessageHeaders.addElement("subject").addText(sThreadSubject);
					if(!sThreadMsgId.isEmpty())
						eMessageHeaders.addElement("message-id").addText(sThreadMsgId);
					
					// Content
					ResultSet rsContents = conn.createStatement().executeQuery("SELECT thread_content_part "+
						"FROM thread_content_part "+
						"WHERE thread_id = " + iThreadId + " " +
						"ORDER BY content_id ASC");
					
					StringBuilder strContent = new StringBuilder();
					
					while(rsContents.next()) {
						String sContentPart = rsContents.getString("thread_content_part");
						strContent.append(sContentPart);
						// [TODO] Ugly
						if(!rsContents.isLast() && 255 != sContentPart.length())
							strContent.append(" ");
					}
					
					eMessage.addElement("content").addCDATA(strContent.toString());
					
					// Attachments
					Element eAttachments = eMessage.addElement("attachments");
					
					ResultSet rsAttachments = conn.createStatement().executeQuery("SELECT file_id, file_name, file_size "+
						"FROM thread_attachments " +
						"WHERE file_name != 'message_source.xml' " + 
						"AND thread_id = " + iThreadId + " " +
						"ORDER BY file_id ASC");
					
					while(rsAttachments.next()) {
						Integer iFileId = rsAttachments.getInt("file_id"); 
						String sFileName = rsAttachments.getString("file_name");
						String sFileSize = rsAttachments.getString("file_size");
						
						Element eAttachment = eAttachments.addElement("attachment");
						eAttachment.addElement("name").setText(sFileName);
						eAttachment.addElement("size").setText(sFileSize);
						eAttachment.addElement("mimetype").setText("");
						
						Element eAttachmentContent = eAttachment.addElement("content");
						eAttachmentContent.addAttribute("encoding", "base64");
						
						// [TODO] Option to ignore huge attachments?
						
						ResultSet rsAttachment = conn.createStatement().executeQuery("SELECT part_content FROM thread_attachments_parts WHERE file_id = " + iFileId);
						
						StringBuilder str = new StringBuilder();
						
						while(rsAttachment.next()) {
							str.append(rsAttachment.getString("part_content"));
						}
						
						eAttachmentContent.addCDATA(new String(Base64.encodeBase64(str.toString().getBytes())));
					}
				}
				
				// Comments

				Element eComments = eTicket.addElement("comments");
				
				ResultSet rsComments = conn.createStatement().executeQuery("SELECT id, date_created, note, user.user_email as worker_email "+
						"FROM next_step "+
						"INNER JOIN user ON (user.user_id=next_step.created_by_agent_id) "+
						"WHERE ticket_id = " + iTicketId + " "+
						"ORDER BY id ASC");
				
				while(rsComments.next()) {
					Integer iCommentCreatedDate = rsComments.getInt("date_created");
					String sCommentAuthor = rsComments.getString("worker_email");
					String sCommentText = rsComments.getString("note");
					
					Element eComment = eComments.addElement("comment");
					eComment.addElement("created_date").setText(iCommentCreatedDate.toString());
					eComment.addElement("author").setText(sCommentAuthor);
					eComment.addElement("text").addCDATA(sCommentText);
				}
				
//				System.out.println(doc.asXML());
				
				OutputFormat format = OutputFormat.createPrettyPrint();
				format.setEncoding("ISO-8859-1");
				format.setOmitEncoding(false);
				XMLWriter writer = new XMLWriter(new FileWriter(outputDir.getPath() + "/" + iTicketId + ".xml"), format); 
				writer.write(doc);
				writer.close();
				
				iCount++;
			}
			
			rsTickets.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
