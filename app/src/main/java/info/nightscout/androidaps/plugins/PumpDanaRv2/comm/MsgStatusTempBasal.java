package info.nightscout.androidaps.plugins.PumpDanaRv2.comm;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;

public class MsgStatusTempBasal extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgStatusTempBasal.class);

    public MsgStatusTempBasal() {
        SetCommand(0x0205);
    }

    public void handleMessage(byte[] bytes) {
        boolean isTempBasalInProgress = (intFromBuff(bytes, 0, 1) & 0x01) == 0x01;
        boolean isAPSTempBasalInProgress = (intFromBuff(bytes, 0, 1) & 0x02) == 0x02;
        int tempBasalPercent = intFromBuff(bytes, 1, 1);
        if (tempBasalPercent > 200) tempBasalPercent = (tempBasalPercent - 200) * 10;
        int tempBasalTotalSec;
        if (intFromBuff(bytes, 2, 1) == 150) tempBasalTotalSec = 15 * 60;
        else if (intFromBuff(bytes, 2, 1) == 160) tempBasalTotalSec = 30 * 60;
        else tempBasalTotalSec = intFromBuff(bytes, 2, 1) * 60 * 60;
        int tempBasalRunningSeconds = intFromBuff(bytes, 3, 3);
        int tempBasalRemainingMin = (tempBasalTotalSec - tempBasalRunningSeconds) / 60;
        Date tempBasalStart = isTempBasalInProgress ? getDateFromTempBasalSecAgo(tempBasalRunningSeconds) : new Date(0);

        DanaRPump pump = DanaRPump.getInstance();
        pump.isTempBasalInProgress = isTempBasalInProgress;
        pump.tempBasalPercent = tempBasalPercent;
        pump.tempBasalRemainingMin = tempBasalRemainingMin;
        pump.tempBasalTotalSec = tempBasalTotalSec;
        pump.tempBasalStart = tempBasalStart;

        updateTempBasalInDB();

        if (Config.logDanaMessageDetail) {
            log.debug("Is temp basal running: " + isTempBasalInProgress);
            log.debug("Is APS temp basal running: " + isAPSTempBasalInProgress);
            log.debug("Current temp basal percent: " + tempBasalPercent);
            log.debug("Current temp basal remaining min: " + tempBasalRemainingMin);
            log.debug("Current temp basal total sec: " + tempBasalTotalSec);
            log.debug("Current temp basal start: " + tempBasalStart);
        }
    }

    @NonNull
    private Date getDateFromTempBasalSecAgo(int tempBasalAgoSecs) {
        return new Date((long) (Math.ceil(new Date().getTime() / 1000d) - tempBasalAgoSecs) * 1000);
    }

    public static void updateTempBasalInDB() {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        DanaRPump danaRPump = DanaRPump.getInstance();
        Date now = new Date();

        try {

            if (treatmentsInterface.isTempBasalInProgress()) {
                TempBasal tempBasal = treatmentsInterface.getTempBasal(new Date().getTime());
                if (danaRPump.isTempBasalInProgress) {
                    if (tempBasal.percent != danaRPump.tempBasalPercent) {
                        // Close current temp basal
                        tempBasal.timeEnd = now;
                        MainApp.getDbHelper().getDaoTempBasals().update(tempBasal);
                        // Create new
                        TempBasal newTempBasal = new TempBasal();
                        newTempBasal.timeStart = now;
                        newTempBasal.percent = danaRPump.tempBasalPercent;
                        newTempBasal.isAbsolute = false;
                        newTempBasal.duration = danaRPump.tempBasalTotalSec / 60;
                        newTempBasal.isExtended = false;
                        MainApp.getDbHelper().getDaoTempBasals().create(newTempBasal);
                        MainApp.bus().post(new EventTempBasalChange());
                    }
                } else {
                    // Close current temp basal
                    tempBasal.timeEnd = now;
                    MainApp.getDbHelper().getDaoTempBasals().update(tempBasal);
                    MainApp.bus().post(new EventTempBasalChange());
                }
            } else {
                if (danaRPump.isTempBasalInProgress) {
                    // Create new
                    TempBasal newTempBasal = new TempBasal();
                    newTempBasal.timeStart = now;
                    newTempBasal.percent = danaRPump.tempBasalPercent;
                    newTempBasal.isAbsolute = false;
                    newTempBasal.duration = danaRPump.tempBasalTotalSec / 60;
                    newTempBasal.isExtended = false;
                    MainApp.getDbHelper().getDaoTempBasals().create(newTempBasal);
                    MainApp.bus().post(new EventTempBasalChange());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}