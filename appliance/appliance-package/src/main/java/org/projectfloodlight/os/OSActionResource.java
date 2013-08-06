package org.projectfloodlight.os;

import java.util.Date;
import java.util.EnumSet;

import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.PowerAction;
import org.projectfloodlight.os.model.RegenKeysAction;
import org.projectfloodlight.os.model.SetPasswordAction;
import org.projectfloodlight.os.model.SetShellAction;
import org.projectfloodlight.os.model.TimeAction;
import org.projectfloodlight.os.model.PowerAction.Action;
import org.projectfloodlight.os.model.SetShellAction.Shell;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.annotation.BigDBInsert;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.data.annotation.BigDBPath;
import net.bigdb.data.annotation.BigDBQuery;
import net.bigdb.data.serializers.ISODateDataNodeSerializer;
import net.bigdb.query.Step;
import net.floodlightcontroller.bigdb.FloodlightResource;

/**
 * Resource for handling OS system actions from the REST API
 * @author readams
 */
public class OSActionResource extends FloodlightResource {

    @BigDBInsert
    @BigDBPath("system-user/set-shell")
    public void setShell(@BigDBParam("mutation-data") DataNode data) 
            throws BigDBException {
        try {            
            OSAction action = new OSAction();
            SetShellAction ssa = new SetShellAction();
            action.setSetShellAction(ssa);
            
            DataNode userName = data.getChild(Step.of("user-name"));
            DataNode shell = data.getChild(Step.of("shell"));

            if (!userName.isNull() && !userName.isValueNull() &&
                !shell.isNull() && !shell.isValueNull()) {
                ssa.setUser(userName.getString());
                ssa.setShell(Shell.forValue(shell.getString()));
                doAction(action);
            }
            
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException("Failed to set system user shell: " + 
                                     e.getMessage(), e);
        }
    }

    @BigDBInsert
    @BigDBPath("system-user/reset-password")
    public void resetPassword(@BigDBParam("mutation-data") DataNode data) 
            throws BigDBException {
        try {            
            OSAction action = new OSAction();
            SetPasswordAction a = new SetPasswordAction();
            action.setSetPasswordAction(a);
            
            DataNode userName = data.getChild(Step.of("user-name"));
            DataNode password = data.getChild(Step.of("password"));

            if (!userName.isNull() && !userName.isValueNull() &&
                !password.isNull() && !password.isValueNull()) {
                a.setUser(userName.getString());
                a.setPassword(password.getString());
                doAction(action);
            }
            
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException("Failed to set system user password: " + 
                                     e.getMessage(), e);
        }
    }

    @BigDBInsert
    @BigDBPath("power")
    public void power(@BigDBParam("mutation-data") DataNode data) 
            throws BigDBException {
        try {            
            OSAction action = new OSAction();
            PowerAction a = new PowerAction();
            action.setPowerAction(a);
            
            DataNode act = data.getChild(Step.of("action"));

            if (!act.isNull() && !act.isValueNull()) {
                a.setAction(Action.forValue(act.getString()));
                doAction(action);
            }
            
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException("Failed to perform power action: " + 
                                     e.getMessage(), e);
        }
    }

    @BigDBInsert
    @BigDBPath("services/regenerate-keys")
    public void regenerateKeys(@BigDBParam("mutation-data") DataNode data) 
            throws BigDBException {
        try {            
            OSAction action = new OSAction();
            RegenKeysAction a = new RegenKeysAction();
            action.setRegenKeysAction(a);
            
            DataNode actions = data.getChild(Step.of("action"));
            EnumSet<RegenKeysAction.Action> actSet =
                    EnumSet.noneOf(RegenKeysAction.Action.class);

            if (!actions.isNull() && !actions.isValueNull()) {
                for (DataNode act : actions) {
                    actSet.add(RegenKeysAction.Action.forValue(act.getString()));
                }
                if (actSet.size() > 0) {
                    a.setActions(actSet.toArray(new RegenKeysAction.Action[0]));
                    doAction(action);
                }
            }
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException("Failed to regenerate keys: " + 
                                     e.getMessage(), e);
        }
    }

    @BigDBInsert
    @BigDBPath("time/ntp")
    public void ntpDate(@BigDBParam("mutation-data") DataNode data) 
            throws BigDBException {
        try {            
            OSAction action = new OSAction();
            TimeAction a = new TimeAction();
            action.setTimeAction(a);
            
            DataNode server = data.getChild(Step.of("ntp-server"));
            
            if (!server.isNull() && !server.isValueNull()) {
                a.setNtpServer(server.getString());
                doAction(action);
            }
            
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException("Failed to reset time using NTP: " + 
                                     e.getMessage(), e);
        }
    }
    
    @BigDBInsert
    @BigDBPath("time/system-time")
    public void setSystemTime(@BigDBParam("mutation-data") DataNode data) 
            throws BigDBException {
        try {            
            OSAction action = new OSAction();
            TimeAction a = new TimeAction();
            action.setTimeAction(a);
            
            if (!data.isNull() && !data.isValueNull()) {
                Date t = ISODateDataNodeSerializer.parse(data.getString());
                a.setSystemTime(t);
                doAction(action);
            }
            
        } catch (BigDBException e) {
            throw e;
        } catch (Exception e) {
            throw new BigDBException("Failed to set system clock: " + 
                                     e.getMessage(), e);
        }
    }

    @BigDBQuery
    @BigDBPath("time/system-time")
    public String getSystemTime() throws BigDBException {
        return ISODateDataNodeSerializer.formatISO(new Date());
    }
    
    private void doAction(OSAction action) throws Exception {
        IOSConfigService configService =
                getModuleContext().getServiceImpl(IOSConfigService.class);

        WrapperOutput wo = configService.applyAction(action);
        if (!wo.succeeded()) {
            throw new Exception("Could not execute action: " + 
                                wo.getOverallStatus());
        }
    }
}
