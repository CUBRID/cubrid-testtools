package com.navercorp.cubridqa.shell.deploy;

import com.jcraft.jsch.JSchException;
import com.navercorp.cubridqa.shell.common.Log;
import com.navercorp.cubridqa.shell.common.SSHConnect;
import com.navercorp.cubridqa.shell.common.ShellInput;
import com.navercorp.cubridqa.shell.main.Context;

public class DeployHA {
	Context context;
	String masterEnvId;
	String slaveIp;
	SSHConnect ssh;
	String port, user, pwd, masterHost;
	String envIdentify;
	
	Log log;
	
	public DeployHA(Context context, String masterEnvId, String slaveEnvIP, Log log) throws JSchException{
		this.context = context;
		this.masterEnvId = masterEnvId;
		this.slaveIp = slaveEnvIP;
		
		port = context.getInstanceProperty(masterEnvId, "ssh.port");
		user = context.getInstanceProperty(masterEnvId, "ssh.user");
		pwd = context.getInstanceProperty(masterEnvId, "ssh.pwd");
		masterHost = context.getInstanceProperty(masterEnvId, "ssh.host");
		
		envIdentify = "MasterEnvId=" + masterEnvId + "[" + user+"@"+ masterHost +":" + port + "] - SlaveEnvId:" + slaveIp;
		this.ssh = new SSHConnect(masterHost, port, user, pwd, context.getServiceProtocolType());
		this.log = log;
	}
	
	
	public void deploy()
	{
		log.print("===== Start to update HA.properties =====");
		ShellInput scripts = new ShellInput();
		scripts.addCommand("ini.sh -u MASTER_SERVER_IP=" + masterHost + " $init_path/HA.properties");
		scripts.addCommand("ini.sh -u MASTER_SERVER_USER=" + this.user + " $init_path/HA.properties");
		scripts.addCommand("ini.sh -u MASTER_SERVER_PW=" + this.pwd + " $init_path/HA.properties ");
		scripts.addCommand("ini.sh -u MASTER_SERVER_SSH_PORT=" + this.port + " $init_path/HA.properties");
		
		scripts.addCommand("ini.sh -u SLAVE_SERVER_IP=" + slaveIp + " $init_path/HA.properties");
		scripts.addCommand("ini.sh -u SLAVE_SERVER_USER=" + this.user + " $init_path/HA.properties");
		scripts.addCommand("ini.sh -u SLAVE_SERVER_PW=" + this.pwd + " $init_path/HA.properties ");
		scripts.addCommand("ini.sh -u SLAVE_SERVER_SSH_PORT=" + this.port + " $init_path/HA.properties");
		
		scripts.addCommand("cubrid_port_id_value=`ini.sh -s 'common' $CUBRID/conf/cubrid.conf cubrid_port_id`");
		scripts.addCommand("ini.sh -u CUBRID_PORT_ID=$cubrid_port_id_value $init_path/HA.properties");
		scripts.addCommand("cubrid_master_shm_id_value=`ini.sh -s 'broker' $CUBRID/conf/cubrid_broker.conf MASTER_SHM_ID`");
		scripts.addCommand("ini.sh -u MASTER_SHM_ID=$cubrid_master_shm_id_value $init_path/HA.properties");
		scripts.addCommand("cubrid_broker1_port_value=`ini.sh -s '%query_editor' $CUBRID/conf/cubrid_broker.conf BROKER_PORT` ");
		scripts.addCommand("ini.sh -u BROKER_PORT1=$cubrid_broker1_port_value $init_path/HA.properties ");
		scripts.addCommand("cubrid_broker1_app_server_shm_value=`ini.sh -s '%query_editor' $CUBRID/conf/cubrid_broker.conf APPL_SERVER_SHM_ID`");
		scripts.addCommand("ini.sh -u APPL_SERVER_SHM_ID1=$cubrid_broker1_app_server_shm_value $init_path/HA.properties ");
		scripts.addCommand("cubrid_broker2_port_value=`ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf BROKER_PORT` ");
		scripts.addCommand("ini.sh -u BROKER_PORT2=$cubrid_broker2_port_value $init_path/HA.properties ");
		scripts.addCommand("cubrid_broker2_app_server_shm_value=`ini.sh -s '%BROKER1' $CUBRID/conf/cubrid_broker.conf APPL_SERVER_SHM_ID`");
		scripts.addCommand("ini.sh -u APPL_SERVER_SHM_ID2=$cubrid_broker2_app_server_shm_value $init_path/HA.properties");
		
		String haPortId = context.getInstanceProperty(this.masterEnvId, "ha.ha_port_id"); 
		if(haPortId != null && haPortId.length()>0){
			scripts.addCommand("ini.sh -u HA_PORT_ID=" + haPortId + " $init_path/HA.properties");
		}else{
			scripts.addCommand("ini.sh -u HA_PORT_ID=59901 $init_path/HA.properties");
		}
		
		String result="";
		try {
			result = ssh.execute(scripts);
			log.println(result);
		} catch (Exception e) {
			e.printStackTrace();
			log.print("[ERROR] " + e.getMessage());
		}
		
		log.print("===== End to update HA.properties =====");
	}
	
	public void close() {
		ssh.close();
	}
}
