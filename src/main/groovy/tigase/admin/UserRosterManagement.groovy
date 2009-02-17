package tigase.admin

import tigase.server.*
import tigase.xmpp.*
import tigase.xmpp.impl.roster.*
import tigase.xml.*
import tigase.db.UserRepository

class Field { String name; String label; String type; String defVal = ""}

class RosterChangesControler {
	UserRepository repository 
	Map<String, XMPPSession> sessions

	Field addOperation = new Field(name: "addJid", label: "Add")
	Field removeOperation = new Field(name: "removeJid", label: "Remove")
	List<Field> operationTypes = [addOperation, removeOperation]

	Field subscriptionNone = new Field(name: "none", label: "None")
	Field subscriptionFrom = new Field(name: "from", label: "From")
	Field subscriptionTo = new Field(name: "to", label: "To")
	Field subscriptionBoth = new Field(name: "both", label: "Both")
	List<Field> subscriptionTypes = [subscriptionNone, subscriptionFrom, subscriptionTo, subscriptionBoth]

	Field ownerJid = new Field(name: "rosterOwnerJID", label: "Roster owner JID", type: "jid-single")
	Field jidToChange= new Field(name: "jidToManipulate", label: "JID to manipulate", type: "jid-single")
	Field operationType = new Field(name: "operationType", label: "Operation type", 
			defVal: addOperation.name)
	Field subscriptionType = new Field(name: "subscriptionType", 
			label: "Subscription type", defVal: subscriptionBoth.name)
	List<Field> formFields = [ownerJid, jidToChange, operationType, subscriptionType]
	
	def addField(Packet form, Field field, List<Field> listFields = []) {
		if (listFields != null && listFields.size() == 0)
			Command.addFieldValue(form, field.name, field.defVal, field.type, field.label) 	
		else {
			def listValues = (listFields.collect { it.name }).toArray(new String[0])
			def listLabels = (listFields.collect { it.label }).toArray(new String[0])
			Command.addFieldValue(form, field.name, field.defVal, field.label, listLabels, listValues)
		}
	}

	def getFieldValue(Packet form, Field field) { return Command.getFieldValue(form, field.name) }		
		
	def processPacket(Packet p) {
		if ((formFields.find { Command.getFieldValue(p, it.name) == null}) == null) {
			String ownerJidStr = getFieldValue(p, ownerJid)
			String jidToManipulate = getFieldValue(p, jidToChange)
			String operationTypeStr = getFieldValue(p, operationType)
			String subscriptionTypeStr = getFieldValue(p, subscriptionType)
			
			Queue<Packet> results;
			if (operationTypeStr == addOperation.name)
				results = addJidToRoster(ownerJidStr, jidToManipulate, subscriptionTypeStr)
			else
				results = removeJidFromRoster(ownerJidStr, jidToManipulate)	
			
			Packet result = p.commandResult(Command.DataType.result)
			Command.addTextField(result, "Note", "Operation successful");
			results.add(result)
			return results
		}
		else {
			Packet result = p.commandResult(Command.DataType.form)
			addField(result, ownerJid)
			addField(result, jidToChange)
			addField(result, operationType, operationTypes)
			addField(result, subscriptionType, subscriptionTypes)		
			return result
		}
	}
		
	def getActiveConnections(String ownerJid) {
		XMPPSession session = sessions.get(ownerJid)
		return (session == null) ? [] : session.getActiveResources();
	}
	
	def subscription(String str) { return RosterAbstract.SubscriptionType.valueOf(str) }

	Queue<Packet> updateLiveRoster(String jid, String jidToChange, boolean remove, String subStr) {
		RosterAbstract rosterUtil = RosterFactory.getRosterImplementation(true)
		Queue<Packet> packets = new LinkedList<Packet>()
		List<XMPPResourceConnection> activeConnections = getActiveConnections(jid)
		for (XMPPResourceConnection conn : activeConnections) {
			if (remove == false) {
				rosterUtil.addBuddy(conn, jidToChange, jidToChange, "")
				rosterUtil.setBuddySubscription(conn, subscription(subStr), jidToChange)
				rosterUtil.updateBuddyChange(conn, packets,
						rosterUtil.getBuddyItem(conn, jidToChange))				
			} else {
				Element it = new Element("item")
				it.setAttribute("jid", jidToChange)
				it.setAttribute("subscription", "remove")
				rosterUtil.updateBuddyChange(conn, packets, it)
				rosterUtil.removeBuddy(conn, jidToChange) 
			}
		}
		return packets
	}	
	
	def modifyDbRoster(String ownerJid, modifyFunc) {
		String rosterStr = repository.getData(ownerJid, null, RosterAbstract.ROSTER, null)
		rosterStr = (rosterStr == null) ? "" : rosterStr
		Map<String, RosterElement> roster = new LinkedHashMap<String, RosterElement>()
		RosterFlat.parseRoster(rosterStr, roster)
		modifyFunc(roster)
		StringBuilder sb = new StringBuilder();
		for (RosterElement relem: roster.values())
			sb.append(relem.getRosterElement().toString());
		repository.setData(ownerJid, null, RosterAbstract.ROSTER, sb.toString());		
	}
	
	Queue<Packet> addJidToRoster(ownerJid, jidToAdd, subscriptionType) {
		List<XMPPResourceConnection> activeConnections = getActiveConnections(ownerJid)
		if (activeConnections.size() == 0) {
			modifyDbRoster(ownerJid, { roster -> 
				RosterElement userToAdd = roster.get(jidToAdd)
				if (userToAdd == null) {
					userToAdd = new RosterElement(
							jidToAdd, jidToAdd, new String[0])
				}
				userToAdd.setSubscription(subscription(subscriptionType))
				roster.put(jidToAdd, userToAdd)								
			})
			return new LinkedList<Packet>()
		} 
		else
			return updateLiveRoster(ownerJid, jidToAdd, false, subscriptionType)
	}
	
	Queue<Packet> removeJidFromRoster(ownerJid, jidToRemove) {
		List<XMPPResourceConnection> activeConnections = getActiveConnections(ownerJid)
		if (activeConnections.size() == 0) {
			modifyDbRoster(ownerJid, { roster -> 
				RosterElement userToRemove = roster.get(jidToRemove)
				if (userToRemove == null) {
					throw new Exception("User to be deleted is not on roster")
				}
				roster.remove(jidToRemove)
			})
			return new LinkedList<Packet>()
		} 
		else
			return updateLiveRoster(ownerJid, jidToRemove, true, "")
	}	
}

new RosterChangesControler(repository: userRepository, 
		sessions: userSessions).processPacket((Packet)packet)