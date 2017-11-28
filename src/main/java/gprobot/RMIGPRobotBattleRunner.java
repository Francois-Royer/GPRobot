package gprobot;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by Fanfan on 25/11/2017.
 */
public interface RMIGPRobotBattleRunner extends Remote {

    void setOpponentsName(String[] names) throws RemoteException;
    void setOpponentsSkills(double []skills) throws RemoteException;
    
    double getRobotFitness(String robot) throws RemoteException;
    void stopRunner() throws RemoteException;
}
