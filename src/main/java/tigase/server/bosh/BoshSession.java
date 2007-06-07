/*  Tigase Project
 *  Copyright (C) 2001-2007
 *  "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.server.bosh;

import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import tigase.server.Packet;
import tigase.xml.Element;

import static tigase.server.bosh.Constants.*;

/**
 * Describe class BoshSession here.
 *
 *
 * Created: Tue Jun  5 18:07:23 2007
 *
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class BoshSession {

	protected static final String CONTENT_ATTR = "content";

	private UUID sid = null;
	private Map<UUID, BoshIOService> connections =
		new LinkedHashMap<UUID, BoshIOService>();
	private long max_wait = MAX_WAIT_DEF_PROP_VAL;
	private long min_polling = MIN_POLLING_PROP_VAL;
	private long max_inactivity = MAX_INACTIVITY_PROP_VAL;
	private int concurrent_requests = CONCURRENT_REQUESTS_PROP_VAL;
	private int hold_requests = HOLD_REQUESTS_PROP_VAL;
	private long max_pause = MAX_PAUSE_PROP_VAL;
	private String domain = null;

	/**
	 * Creates a new <code>BoshSession</code> instance.
	 *
	 */
	public BoshSession(String def_domain) {
		this.sid = UUID.randomUUID();
		this.domain = def_domain;
	}

	public Packet init(Packet packet, BoshIOService service,
		long max_wait, long min_polling, long max_inactivity,
		int concurrent_requests, int hold_requests, long max_pause) {
		long wait_l = max_wait;
		String wait_s = packet.getAttribute(WAIT_ATTR);
		if (wait_s != null) {
			try {
				wait_l = Long.parseLong(wait_s);
			} catch (NumberFormatException e) {
				wait_l = max_wait;
			}
		}
		this.max_wait = Math.min(wait_l, max_wait);
		int hold_i = hold_requests;
		String hold_s = packet.getAttribute(HOLD_ATTR);
		if (hold_s != null) {
			try {
				hold_i = Integer.parseInt(hold_s);
			} catch (NumberFormatException e) {
				hold_i = hold_requests;
			}
		}
		this.hold_requests = Math.max(hold_i, hold_requests);
		if (packet.getAttribute(TO_ATTR) != null) {
			this.domain = packet.getAttribute(TO_ATTR);
		}
		this.min_polling = min_polling;
		this.max_inactivity = max_inactivity;
		this.concurrent_requests = concurrent_requests;
		this.max_pause = max_pause;
		if (packet.getAttribute(CONTENT_ATTR) != null) {
			service.setContentType(packet.getAttribute(CONTENT_ATTR));
		}
		Element body = new Element(BODY_EL_NAME,
			new String[] {WAIT_ATTR,
										INACTIVITY_ATTR,
										POLLING_ATTR,
										REQUESTS_ATTR},
			new String[] {Long.valueOf(this.max_wait).toString(),
										Long.valueOf(this.max_inactivity).toString(),
										Long.valueOf(this.min_polling).toString(),
										Integer.valueOf(this.hold_requests).toString()});
		return new Packet(body);
	}


}