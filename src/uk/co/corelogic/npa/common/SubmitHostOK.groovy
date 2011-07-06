package uk.co.corelogic.npa.common

/**
 * Send back host OK to Nagios
 * @author Chris Gilbert
 */
class SubmitHostOK extends TimerTask {

    void run() {
        MaintenanceUtil.sendHostOk()
    }
}
