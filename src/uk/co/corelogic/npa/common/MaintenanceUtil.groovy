/*
 * This
 */

package uk.co.corelogic.npa.common
import uk.co.corelogic.npa.NPA
import uk.co.corelogic.npa.nagios.*
import java.lang.management.ThreadMXBean
import java.lang.management.ThreadInfo
import java.lang.management.ManagementFactory

/**
 * This class deals with maintenance jobs that need to be run regularly and utility methods.
 * It also contains the host check ping, which simply generates an ok result for the current host, and returns this to Nagios.
 *
 * @author Chris Gilbert
 */
static class MaintenanceUtil {
	
    static config = NPA.getConfigObject()
    static npa_version = "1.3.1_beta1"
    private static hostname = ""
    //static npa_version = System.getProperty("application.version")
    
    // Force singleton
    private MaintenanceUtil() {}

    public static getNPAVersion() {
        return npa_version;
    }
    public static getHostName() {
        if ( hostname == "" ) {
            if ( config.use_os_hostname_command == "true" ) {
                hostname = "hostname".execute().text.trim()
            } else {

            try {
                InetAddress addr = InetAddress.getLocalHost();
                byte[] ipAddr = addr.getAddress();
                hostname = addr.getHostName();
                    Log.debug("Hostname detected as $hostname")
                } catch (UnknownHostException e) {
                    Log.error("Hostname lookup failed!  Please check name resolution is working, or set use_os_hostname_command to true")
                    throw e
                }
           }
        }
        return hostname;
    }

    /*
     * Generate an OK host check and send to Nagios
    */
    public synchronized static void sendHostOk() {
        def hostChk = new CheckResult (null, MaintenanceUtil.getHostName(), "OK", null, "none", "Host is running Nagios Passive Agent version " + MaintenanceUtil.getNPAVersion())
        def result = SendCheckResultHTTP.submit(hostChk)
        if (!result) {
            Log.error("Failed to submit host check back to server!")
        }
    }

    /*
     * Generate a message saying the NPA agent is shutting down, leaving host in unknown state.
    */
    public synchronized static void sendShutdownHost(message) {
        def hostChk = new CheckResult (null, MaintenanceUtil.getHostName(), "UNKNOWN", null, "none", "$message WARNING: NPA Was shutdown (or crashed) at ${new Date()} - Host is running Nagios Passive Agent version " + MaintenanceUtil.getNPAVersion())
        def result = SendCheckResultHTTP.submit(hostChk)
        if (!result) {
            Log.error("Failed to submit host check back to server!")
        }
    }

    /*
     * Generate a message putting the host in a critical state because of problems with checks
    */
    public synchronized static void sendCriticalHost() {
        def hostChk = new CheckResult (null, MaintenanceUtil.getHostName(), "CRITICAL", null, "none", "CRITICAL: Some checks are having problems and may need to be restarted - should restart automatically - Host is running Nagios Passive Agent version " + MaintenanceUtil.getNPAVersion())
        def result = SendCheckResultHTTP.submit(hostChk)
        if (!result) {
            Log.error("Failed to submit host check back to server!")
        }
        println("Wrapper should restart agent now!")
    }


    /*
     * Stop all running timers - worth doing before shutdown to allow JVM to exit quickly
     */
    public synchronized static void stopAllTimers(){
        CheckScheduler.stopAllTimers()
    }

    public static void detectDeadlocks() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] threadIds = bean.findMonitorDeadlockedThreads() // Returns null if no threads are deadlocked.

        if (threadIds != null) {
            ThreadInfo[] infos = bean.getThreadInfo(threadIds);

            for (ThreadInfo info : infos) {
                Log.fatal("Deadlocks detected!! Triggering restart..")
                StackTraceElement[] stack = info.getStackTrace();
                Log.fatal(stack)
                MaintenanceUtil.sendCriticalHost()
            }
        }
    }

}

