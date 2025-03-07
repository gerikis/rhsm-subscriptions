/*
 * Copyright Red Hat, Inc.
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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.StatusResponse;
import com.redhat.swatch.contract.openapi.resource.ApiException;
import com.redhat.swatch.contract.openapi.resource.DefaultApi;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.service.ContractService;
import java.time.OffsetDateTime;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.ProcessingException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ContractsTestingResource implements DefaultApi {

  @Inject ContractService service;

  /**
   * Create contract record in database from provided contract dto payload
   *
   * @param contract
   * @return Contract
   * @throws ApiException
   * @throws ProcessingException
   */
  @Override
  @Transactional
  @RolesAllowed({"test"})
  public Contract createContract(Contract contract) throws ApiException, ProcessingException {
    log.info("Creating contract");
    return service.createContract(contract);
  }

  @Override
  @RolesAllowed({"test"})
  public void deleteContractByUUID(String uuid) throws ApiException, ProcessingException {
    log.info("Deleting contract {}", uuid);
    service.deleteContract(uuid);
  }

  /**
   * Get a list of saved contracts based on URL query parameters
   *
   * @param orgId
   * @param productId
   * @param metricId
   * @param billingProvider
   * @param billingAccountId
   * @return List<Contract> dtos
   * @throws ApiException
   * @throws ProcessingException
   */
  @Override
  @RolesAllowed({"test", "support", "service"})
  public List<Contract> getContract(
      String orgId,
      String productId,
      String metricId,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime timestamp)
      throws ApiException, ProcessingException {
    return service.getContracts(
        orgId,
        productId,
        metricId,
        billingProvider,
        billingAccountId,
        vendorProductCode,
        timestamp);
  }

  @Override
  @Transactional
  @RolesAllowed({"test", "support"})
  public StatusResponse syncAllContracts() throws ApiException, ProcessingException {
    log.info("Syncing All Contracts");
    var contracts = service.getAllContracts();
    if (contracts.isEmpty()) {
      return new StatusResponse().status("No active contract found for the orgIds");
    }
    for (ContractEntity org : contracts) {
      syncContractsByOrg(org.getOrgId());
    }
    return new StatusResponse().status("All Contract are Synced");
  }

  @Override
  @RolesAllowed({"test", "support"})
  public StatusResponse syncContractsByOrg(String orgId) throws ApiException, ProcessingException {
    return service.syncContractByOrgId(orgId);
  }

  @Override
  @RolesAllowed({"test"})
  public StatusResponse createPartnerEntitlementContract(PartnerEntitlementContract contract)
      throws ApiException, ProcessingException {
    return service.createPartnerContract(contract);
  }
}
