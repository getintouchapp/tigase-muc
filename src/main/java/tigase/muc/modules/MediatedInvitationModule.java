/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.modules;

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * @author bmalkow
 * 
 */
public class MediatedInvitationModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("message").add(
			ElementCriteria.name("x", "http://jabber.org/protocol/muc#user").add(ElementCriteria.name("invite")));

	public MediatedInvitationModule(MucConfig config, IMucRepository mucRepository) {
		super(config, mucRepository);
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element) throws MUCException {
		try {
			final String senderJid = element.getAttribute("from");
			final String roomId = getRoomId(element.getAttribute("to"));

			if (getNicknameFromJid(element.getAttribute("to")) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomId);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final Role senderRole = room.getRoleByJid(senderJid);
			if (!senderRole.isInviteOtherUsers()) {
				throw new MUCException(Authorization.NOT_ALLOWED);
			}
			final Affiliation senderAffiliation = room.getAffiliation(senderJid);
			if (room.getConfig().isRoomMembersOnly() && !senderAffiliation.isEditMemberList()) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			final Element x = element.getChild("x");
			final Element invite = x.getChild("invite");
			final Element reason = invite.getChild("reason");
			final String recipient = invite.getAttribute("to");

			final Element resultMessage = new Element("message", new String[] { "from", "to" }, new String[] { roomId, recipient });
			final Element resultX = new Element("x", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/muc#user" });
			resultMessage.addChild(resultX);

			if (room.getConfig().isRoomMembersOnly() && senderAffiliation.isEditMemberList()) {
				room.addAffiliationByJid(recipient, Affiliation.member);
			}

			final Element resultInvite = new Element("invite", new String[] { "from" }, new String[] { senderJid });
			resultX.addChild(resultInvite);

			if (reason != null) {
				resultInvite.addChild(reason.clone());
			}

			return makeArray(resultMessage);
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}