package uk.co.corelogic.npa.common
import uk.co.corelogic.npa.database.*
import uk.co.corelogic.npa.os.*
import uk.co.corelogic.npa.oas.*
import java.util.timer.*
import java.util.Random


abstract class Check extends TimerTask implements Cloneable {

def chk_name
def chk_category
def chk_interval
def chk_th_warn
def chk_th_crit
def chk_th_type
def sockOutput = new StringBuffer()
def threadID;
def initiatorID
def gatherer
def variables

/* These values are the required and optional arguments applicable to all class extending Check.
 * They are be extended by the lists in individual checks.
 */
List required = ["nagiosServiceName"]
List optional = []
Map requiredWith = [:]
Map optionalWith = [:]

    public Check(){
    }

    Check(String chk_name, th_warn, th_crit, String th_type, Map variables) {
        this.chk_name = chk_name
        this.chk_th_warn = th_warn
        this.chk_th_crit = th_crit
        this.chk_th_type = th_type
        this.variables = variables

        if (chk_name == null || th_warn == null || th_crit == null || th_type == null || variables == null ) {
            throw new IllegalArgumentException("Invalid arguments to Check! Require at least chk_name, th_warn, th_crit, th_type, variables.");
        }
        init()
    }


    synchronized public Check makeClone(chk_name) {
        Check clone = CheckFactory.getCheck(chk_name)
        clone.chk_name = this.chk_name;
        clone.chk_category = this.chk_category;
        clone.chk_interval = this.chk_interval;
        clone.chk_th_warn = this.chk_th_warn;
        clone.chk_th_crit = this.chk_th_crit;
        clone.chk_th_type = this.chk_th_type;
        clone.gatherer = null;
        clone.variables = this.variables;
        return clone;
    }

    /*
     * Initialise the class, checking if the supplied variables match the required ones for given Check
     *
     */
    public init() {
        if ( this.initiatorID == null ) {
            this.initiatorID  = UUID.randomUUID();
        }
        validateVariables()
        if (! this.variables ) { throw new NPAException("Missing required variables property in Check type ${chk_name} Please check code!") }
    }

    /*
     * Validate the variables supplied in npa.xml and ensure they match those required for the check
     */
    private void validateVariables()  {

        def missingReq = []
        def missingOpt = []
        def missingReqWith = [:]
        def missingOptWith = [:]

        missingReq = required - (variables.keySet() as List)
        missingOpt = optional - (variables.keySet() as List)


        def foundParents = (requiredWith.keySet() as List).intersect((variables.keySet() as List))
        missingReqWith = foundParents.collect { requiredWith.get(it) - (variables.keySet() as List) }.flatten()

        def foundParents2 = (optionalWith.keySet() as List).intersect((variables.keySet() as List))
        missingOptWith = foundParents2.collect { optionalWith.get(it) - (variables.keySet() as List) }.flatten()
       
        
        if (missingOpt.size() > 0) { Log.warn("Optional elements not specified: ", missingOpt) }
        if (missingOptWith.size() > 0) { Log.warn("Optional elements not specified: ", missingOptWith) }
        if (missingReq.size() > 0) { Log.error("Required arguments not specified!: ", missingReq); throw new NPAException("Required elements were not specified in $chk_name:", missingReq) }
        if (missingReqWith.size() > 0) { Log.error("Required arguments not specified!: ", missingReqWith); throw new NPAException("Required elements were not specified in $chk_name:", missingReqWith) }


    }



    // Constructor for checks triggered from interactive shell
    Check(cmd) {
        def cmdArray = cmd.split()
        try {
            this.sockOutput << "Output:\n"
            this.chk_name = cmdArray[1].toString()
            this.chk_th_warn = cmdArray[2].toDouble()
            this.chk_th_crit = cmdArray[3].toDouble()
            this.chk_th_type = cmdArray[4].toString()

            // Parse the arguments as value pairs using an '=' sign seperator
            def arrLen = cmdArray.length
            if (arrLen > 5) {
                String[] args = cmdArray[5..arrLen-1]
                args.each {
                    def tokens=it.tokenize("=")
                
                    this.variables[(tokens[0])] = tokens[1]
                }
            }
            
            this.run()

        } catch(e) {
            Log.error("Error parsing FMD command. ", e)
            this.sockOutput << e.toString()
            this.sockOutput << "Invalid check arguments!\n"
            this.sockOutput << "Please checks you have supplied all the required arguments for the check."
            this.sockOutput << "Usage: check chk_name(String) th_warn(double) th_crit(double) th_type(String) [argument1=arg1 argument2=arg2 ...]\n"
        }

    }

    String returnString() {
        return this.sockOutput
    }


    /**
     * Generic run method which executes the check
    */
    public void run() {
         try {
            this.threadID = Thread.currentThread().getId();
            CheckScheduler.registerThread(Thread.currentThread(), this.clone());
            Log.debug("Thread $threadID status: " + Thread.currentThread().getState())
            Log.debug("Running check with parameters: ${this.chk_name}, [${this.variables}, ${this.chk_th_warn}, ${this.chk_th_crit}, ${this.chk_th_type}]")
                CheckResultsQueue.add(this.invokeMethod(this.chk_name.trim(), null))

         } catch (OutOfMemoryError e) {
            Log.error("OutOfMemoryError occurred whilst running $chk_name check: ", e)
            Log.error("STACK:", e)
            e.printStackTrace()
            Log.fatal("OutOfMemory Occurred!  Will now attempt to shutdown agent gracefully and set UNKNOWN status for host..")
            println("Agent should restart automatically from wrapper process.")
            MaintenanceUtil.sendShutdownHost("OutofMemoryError in check $chk_name! Shutdown agent.")
            System.exit(1);
        } catch(e) {
            Log.error("Exception occurred whilst running $chk_name check: ", e)
            Log.error("STACK:", e)
            e.printStackTrace()
            Log.error("A SERIOUS ERROR OCCURRED IN CHECK!")
        }
    }



    /**
     * Calculate a nagios status based on values vs thresholds
     *
    */
    public String calculateStatus(th_warn, th_crit, value, th_type) {
        def status = "UNKNOWN"
        if (th_type == null ) { th_type = "GTE" }

        switch ( th_type ) {

            case "GTE":     value = Double.parseDouble(value.toString())
                            if ( value >= th_warn ) { status = "WARNING" }
                            if ( value >= th_crit ) { status = "CRITICAL" }
                            if ( (th_crit > value) && (th_warn > value) ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;

            case "GT":      value = Double.parseDouble(value.toString())
                            if ( value > th_warn ) { status = "WARNING" }
                            if ( value > th_crit ) { status = "CRITICAL" }
                            if ( ( value <= th_warn ) && ( value <= th_crit ) ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;

            case "LTE":     value = Double.parseDouble(value.toString())
                            if ( value <= th_warn ) { status = "WARNING" }
                            if ( value <= th_crit ) { status = "CRITICAL" }
                            if ( (value > th_crit ) && ( value > th_warn ) ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;

            case "LT":      value = Double.parseDouble(value.toString())
                            if ( value < th_warn ) { status = "WARNING" }
                            if ( value < th_crit ) { status = "CRITICAL" }
                            if ( (value >= th_crit) && (value >= th_warn) ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;

            case "EQ":      value = (String) value
                            if ( th_warn == value ) { status = "WARNING" }
                            if ( th_crit == value ) { status = "CRITICAL" }
                            if ( th_warn != value && th_crit !=value ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;

            case "CONTAINS": value = (String) value
                            if ( value =~ th_warn)  { status = "WARNING" }
                            if ( value =~ th_crit)  { status = "CRITICAL" }
                            if ( (! value =~ th_warn) && (! value =~ th_crit) ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;
                            
                            
            default:        value = Double.parseDouble(value.toString())
                            Log.warn("Comparison type did not match known values - will be treated as GTE!")
                            if ( value >= th_warn ) { status = "WARNING" }
                            if ( value >= th_crit ) { status = "CRITICAL" }
                            if ( (th_crit > value) && (th_warn > value) ) { status = "OK" }
                            if ( value == -1 ) { status = "UNKNOWN" }
                            break;
        }
        Log.debug("Status for $chk_name is $status: Type of comparison: $th_type th_warn: $th_warn th_crit: $th_crit value: $value")
        return status
    }

     /**
     * Calculate a nagios status based on a collection of values vs thresholds
     *
    */
    public String calculateStatus(th_warn, th_crit, ArrayList values, th_type) {
        def maxstatus = "UNKNOWN"
        values.each {
            def status = this.calculateStatus(th_warn, th_crit, it, th_type)
            
            if ((status == "OK") && (maxstatus != "CRITICAL")  && (maxstatus != "WARNING")) { maxstatus = "OK" }
            if ((status == "WARNING") && (maxstatus != "CRITICAL")) { maxstatus = "WARNING" }
            if (status == "CRITICAL") { maxstatus = "CRITICAL" }
        }
        return maxstatus
    }


    /**
     * Generate a new CheckResult object for the result of this check
    */
    public CheckResult generateResult(ID, nagiosServiceName, host, status, performance, date, message) {
        try {
            Log.debug("Generating result from: $ID, $nagiosServiceName, $host, $status, $performance, date, $message")
            return new CheckResult(ID, nagiosServiceName, host, status, performance, date, message)
        } catch (e) {
            Log.error("Exception occurred when generating check result.", e)
            Log.error("STACK:", e)
            throw e
        }
    }

    public schedule(interval) {
        this.chk_interval = interval;
        CheckScheduler.schedule(this.clone());
     }


    // Method to execute OS commands
    public runCmd (cmd) {

     def stdout = new StringBuffer()
     def stderr = new StringBuffer()
     def p
     try {
            Log.debug("Running command: $cmd" )
            p = cmd.execute()

            

            Log.debug("Waiting for command to return...")
            Log.debug("Trying to get text: $p.text")
            println("Flushing output.")
            p.out.flush()
            p.waitForProcessOutput(stdout, stderr)
            //p.waitForOrKill(30000)

            if (p.exitValue() == 0 ) {
                Log.debug("Stream: $stdout $stderr")
                println("Exit value was 0.")
                p.out.close()
                return stdout
            } else {
                Log.error("Error occurred: $stderr");
                println("Exit value was not 0.")
                return stdout + stderr
            }
     } catch (e) {
         println("Exception occurred.")
         Log.error("Exception thrown running command!", e)
         throw e
     }
    }

    /*
     * Validate a variables map against a list of required parameters and throw
     * an exception if any are missing
    */
    public validateParameters(Map variables, ArrayList required) {
       def missing
       required.collect {
           if (! variables.containsKey(it)) { missing.add(it) }
       }

        if (! missing.isEmpty() ) {
            throw new NPAException("Variables do not contain required parameters: $missing")
        } 
    }

}