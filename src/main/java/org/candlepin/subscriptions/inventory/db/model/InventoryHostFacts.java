/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.inventory.db.model;

import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents an inventory host's facts.
 */
public class InventoryHostFacts {
    private String account;
    private String displayName;
    private String orgId;
    private int cores;
    private int sockets;
    private String syncTimestamp;
    private Set<String> products;
    private int systemProfileCoresPerSocket;
    private int systemProfileSockets;
    private Set<String> qpcProducts;
    private Set<String> qpcProductIds;
    private Set<String> systemProfileProductIds;
    private String syspurposeRole;

    @SuppressWarnings("squid:S00107")
    public InventoryHostFacts(String account, String displayName, String orgId, String cores, String sockets,
        String products, String syncTimestamp, String systemProfileCores, String systemProfileSockets,
        String qpcProducts, String qpcProductIds, String systemProfileProductIds, String syspurposeRole) {

        this.account = account;
        this.displayName = displayName;
        this.orgId = orgId;
        this.cores = asInt(cores);
        this.sockets = asInt(sockets);
        this.products = asStringSet(products);
        this.qpcProducts = asStringSet(qpcProducts);
        this.qpcProductIds = asStringSet(qpcProductIds);
        this.syncTimestamp = StringUtils.hasText(syncTimestamp) ? syncTimestamp : "";
        this.systemProfileCoresPerSocket = asInt(systemProfileCores);
        this.systemProfileSockets = asInt(systemProfileSockets);
        this.systemProfileProductIds = asStringSet(systemProfileProductIds);
        this.syspurposeRole = syspurposeRole;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public Integer getCores() {
        return cores;
    }

    public void setCores(Integer cores) {
        this.cores = cores;
    }

    public Integer getSockets() {
        return sockets;
    }

    public void setSockets(Integer sockets) {
        this.sockets = sockets;
    }

    public String getSyncTimestamp() {
        return syncTimestamp;
    }

    public void setSyncTimestamp(String syncTimestamp) {
        this.syncTimestamp = syncTimestamp;
    }

    public Set<String> getProducts() {
        return products;
    }

    public void setProducts(Set<String> products) {
        this.products = products;
    }

    private int asInt(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.valueOf(value) : 0;
        }
        catch (NumberFormatException nfe) {
            return 0;
        }
    }

    private Set<String> asStringSet(String productJson) {
        if (!StringUtils.hasText(productJson)) {
            return new HashSet<>();
        }
        return StringUtils.commaDelimitedListToSet(productJson);
    }
    public int getSystemProfileCoresPerSocket() {
        return systemProfileCoresPerSocket;
    }

    public int getSystemProfileSockets() {
        return systemProfileSockets;
    }

    public Set<String> getQpcProducts() {
        return qpcProducts;
    }

    public Set<String> getQpcProductIds() {
        return qpcProductIds;
    }

    public Set<String> getSystemProfileProductIds() {
        return systemProfileProductIds;
    }

    public String getSyspurposeRole() {
        return syspurposeRole;
    }

    public void setSyspurposeRole(String syspurposeRole) {
        this.syspurposeRole = syspurposeRole;
    }
}
