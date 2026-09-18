package com.qrserve.merchant.entity;

/**
 * Subscription tier. FREE is the default for every merchant, including one
 * auto-provisioned via Super App token exchange (SuperAppProvisioningService)
 * — there is no billing flow yet, so nothing ever sets PRO today except a
 * direct database update. See BranchService.createBranch for the one limit
 * currently enforced against this (max 1 branch on FREE).
 */
public enum MerchantTier {
    FREE,
    PRO
}
