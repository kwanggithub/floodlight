package org.projectfloodlight.os.linux.ubuntu;

import static org.projectfloodlight.os.ConfigletUtil.run;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSActionlet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.WrapperOutput.Item;
import org.projectfloodlight.os.WrapperOutput.Status;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.RegenKeysAction;

public class UbuntuRegenKeysActionlet implements IOSActionlet {
    static final String[] SSH_KEYS = 
        {"etc/ssh/ssh_host_dsa_key", "etc/ssh/ssh_host_ecdsa_key",
         "etc/ssh/ssh_host_rsa_key", "etc/ssh/ssh_host_dsa_key.pub",
         "etc/ssh/ssh_host_ecdsa_key.pub", "etc/ssh/ssh_host_rsa_key.pub"};
    static final String[] REGEN_SSH = 
        {"/usr/sbin/dpkg-reconfigure", 
         "-f", "noninteractive", "openssh-server"};
    static final String[] REGEN_SNAKEOIL =
        {"/usr/sbin/make-ssl-cert",
         "generate-default-snakeoil", "--force-overwrite"};
    static final String[] NGINX_RESTART = 
        {"/usr/sbin/invoke-rc.d", "nginx", "restart"};

    @Override
    public EnumSet<ActionType> provides() {
        return EnumSet.of(ActionType.REGENERATE_KEYS);
    }

    @Override
    public WrapperOutput applyAction(File basePath, OSAction action) {
        WrapperOutput wo = new WrapperOutput();
        RegenKeysAction rka = action.getRegenKeysAction();
        for (RegenKeysAction.Action a : rka.getActions()) {
            switch (a) {
                case SSH:
                    wo.add(regenSSHKey(basePath));
                    break;
                case WEB_SSL:
                    wo.add(regenSnakeOil(basePath));
                    break;
            }  
        }
        return wo;
    }

    private WrapperOutput regenSnakeOil(File basePath) {
        WrapperOutput wo = new WrapperOutput();
        wo.add(run(REGEN_SNAKEOIL));
        wo.add(run(NGINX_RESTART));
        return wo;
    }

    private WrapperOutput regenSSHKey(File basePath) {
        WrapperOutput wo = new WrapperOutput();

        for (String key : SSH_KEYS) {
            File kf = new File(basePath, key);
            if (kf.exists()) {
                Item i = new Item();
                i.setAction("Deleting " + key);
                
                if (kf.delete()) {
                    i.setStatus(Status.SUCCESS);
                } else {
                    i.setStatus(Status.IO_ERROR);
                    i.setMessage("Failed to delete");
                }
                wo.addItem(i);
            }
        } 

        wo.add(run(REGEN_SSH));
        return wo;
    }

}
