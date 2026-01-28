package org.asf.quicktools.server.vhost;

import java.io.IOException;
import java.util.HashMap;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.RemoteClient;
import org.asf.connective.objects.HttpRequest;
import org.asf.connective.objects.HttpResponse;

public class VhostsContentSource extends ContentSource {

	private VhostDefinition preferredDefault;
	private HashMap<String, VhostDefinition> vhostsByDomain;
	private HashMap<String, VhostDefinition> vhostsByDomainWildcards;
	private String handlerSetTopTypeDefault;
	private String contentSourceTopTypeDefault;

	public VhostsContentSource(HashMap<String, VhostDefinition> vhostsByDomain,
			HashMap<String, VhostDefinition> vhostsByDomainWildcards, String handlerSetTopTypeDefault,
			String contentSourceTopTypeDefault, VhostDefinition preferredDefault) {
		this.vhostsByDomain = vhostsByDomain;
		this.vhostsByDomainWildcards = vhostsByDomainWildcards;
		this.handlerSetTopTypeDefault = handlerSetTopTypeDefault;
		this.contentSourceTopTypeDefault = contentSourceTopTypeDefault;
		this.preferredDefault = preferredDefault;
	}

	@Override
	public boolean process(String path, HttpRequest request, HttpResponse response, RemoteClient client,
			ConnectiveHttpServer server) throws IOException {
		// Check host
		if (request.hasHeader("Host")) {
			// Parse header
			String host = request.getHeaderValue("Host"); // Without port
			String hostFull = host; // With port
			String hostSubstringer = host;
			if (hostSubstringer.contains("[")) {
				hostSubstringer = hostSubstringer.substring(host.indexOf("]") + 1);
			}
			if (hostSubstringer.contains(":")) {
				// Has port
				host = host.substring(0, host.lastIndexOf(":"));
			}

			// Check if host is present
			VhostDefinition vhost = null;
			if (vhostsByDomain.containsKey(hostFull.toLowerCase())) {
				// Found
				vhost = vhostsByDomain.get(hostFull.toLowerCase());
			} else if (vhostsByDomain.containsKey(host.toLowerCase())) {
				// Found
				vhost = vhostsByDomain.get(host.toLowerCase());
			} else if (host.contains(".")) {
				// Try wildcard
				String domain = host;
				String domainFull = hostFull;
				while (true) {
					// Find domain
					if (vhostsByDomainWildcards.containsKey(domainFull.toLowerCase())) {
						// Found
						vhost = vhostsByDomainWildcards.get(domainFull.toLowerCase());
						break;
					} else if (vhostsByDomainWildcards.containsKey(domain.toLowerCase())) {
						// Found
						vhost = vhostsByDomainWildcards.get(domain.toLowerCase());
						break;
					}

					// Check if at top
					if (!domain.contains("."))
						break;

					// Go up
					domain = domain.substring(0, domain.indexOf("."));
					domainFull = domainFull.substring(0, domainFull.indexOf("."));
				}
			}

			// Check result
			if (vhost == null)
				vhost = preferredDefault;
			if (vhost != null) {
				// Check source
				ContentSource source = vhost.source;
				if (source != null) {
					// Run
					return source.process(path, request, response, client, server);
				}
			}
		}

		// Fallback to parent
		return runParent(path, request, response, client, server);
	}

}
