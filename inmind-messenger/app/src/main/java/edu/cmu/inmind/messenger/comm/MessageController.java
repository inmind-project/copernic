package edu.cmu.inmind.messenger.comm;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import com.sendbird.android.BaseMessage;

import edu.cmu.inmind.messenger.R;
import edu.cmu.inmind.messenger.groupchannel.GroupChatAdapter;
import edu.cmu.inmind.messenger.utils.PreferenceUtils;

import java.util.Random;

import edu.cmu.inmind.multiuser.communication.ClientCommController;
import edu.cmu.inmind.multiuser.controller.common.CommonUtils;
import edu.cmu.inmind.multiuser.controller.common.Constants;
import edu.cmu.inmind.multiuser.controller.communication.ResponseListener;
import edu.cmu.inmind.multiuser.controller.communication.SessionMessage;
import edu.cmu.inmind.multiuser.log.LogC;

import static edu.cmu.inmind.messenger.utils.Constants.INMIND;

/**
 * Created by oscarr on 6/8/18.
 */

public class MessageController implements ResponseListener{
    private static MessageController instance;
    private ClientCommController clientCommController;
    private String sessionId;
    private GroupChatAdapter chatAdapter;
    private Activity activity;
    private Random random;
    private Context ctx;

    private MessageController() {
        this.sessionId = PreferenceUtils.getUserId();
        random = new Random();
    }

    public static MessageController getInstance(){
        if(instance == null){
            instance = new MessageController();
        }
        return instance;
    }

    public void setChatAdapter(GroupChatAdapter chatAdapter) {
        this.chatAdapter = chatAdapter;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
        this.ctx = activity.getApplicationContext();
    }

    public void connect(){
        clientCommController = new ClientCommController.Builder(new LogC() )
                .setServerAddress( "tcp://inmind-harlequin.ddns.net:5555" )
                .setSessionId(sessionId)
                .setRequestType(Constants.REQUEST_CONNECT)
                .setResponseListener(this)
                .build();
    }

    public void send(final String message){
        send(message, 0);
    }

    public void send(final String message, final long delay){
        CommonUtils.execute(new Runnable() {
            @Override
            public void run() {
                if( delay > 0 ) CommonUtils.sleep(delay);
                SessionMessage sessionMessage = new SessionMessage();
                sessionMessage.setPayload(message);
                sessionMessage.setSessionId(sessionId);
                clientCommController.send(sessionId, sessionMessage);
            }
        });
    }

    public void disconnect(){
        CommonUtils.execute(new Runnable() {
            @Override
            public void run() {
                clientCommController.disconnect(sessionId);
            }
        });
    }

    @Override
    public void process(final String message) {
        // message from InMind
        final SessionMessage sessionMessage = CommonUtils.fromJson(message, SessionMessage.class);
        if( !sessionMessage.getPayload().equals(INMIND + "ACK") ) {
            if (!message.contains(Constants.SESSION_INITIATED)
                    && !message.contains(Constants.SESSION_RECONNECTED)) {
                if (activity != null) {
                    if (sessionMessage.getPayload() != null && !sessionMessage.getPayload().isEmpty() &&
                            !sessionMessage.getPayload().equals(INMIND)) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                chatAdapter.addFirst(BaseMessage.buildFromSerializedData(
                                        new InMindMessage(String.valueOf(random.nextLong()),
                                                sessionMessage.getPayload()).serialize()));
                            }
                        });
                    }
                    showNotification(sessionMessage.getMessageId());
                }
            } else {
                // Let's tell MUF that we are ready to start conversation
                send("Hi", 2000);
            }
        }
    }


    public void showNotification(String notificationType){
        int mNotificationID = -1; //Make sure this number is unique, we use this to update or cancel notification.
        int smallIcon = -1, bigIcon = -1;
        String title = "";
        String description = "";
        String summary = "";

        if(notificationType != null) {
            switch (notificationType) {
                case "PHARMACY":
                    mNotificationID = 1;
                    title = "CVS Pharmacy";
                    description = "CVS Discount Coupons";
                    summary = "You have some discount coupons waiting for you";
                    smallIcon = R.drawable.extracare_small;
                    bigIcon = R.drawable.extracare;
                    break;

                case "ORGANIC":
                    mNotificationID = 2;
                    title = "Whole Foods Market";
                    description = "Match with your preferences!";
                    summary = "There's a match with your preferences! WholeFoods offers Organic and Non-GMO foods!";
                    smallIcon = R.drawable.whole_foods_small;
                    bigIcon = R.drawable.whole_foods_big;
                    break;

            }

            if (mNotificationID != -1) {
                //Sound & icon related to notification
                Uri mNotificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Bitmap bitmap = BitmapFactory.decodeResource(ctx.getResources(), bigIcon);
                //Build the object and set the title, text, sound etc.. properties
                NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, "channelId")
                        .setLargeIcon(bitmap)
                        .setContentTitle(title)
                        .setContentText(description)
                        .setSound(mNotificationSoundUri)
                        .setSmallIcon(smallIcon) //to small icon on the right hand side
                        .setWhen(System.currentTimeMillis()); // Displays the date on right side bottom

                NotificationCompat.BigPictureStyle s =
                        new NotificationCompat.BigPictureStyle().bigPicture(bitmap);
                s.setSummaryText(summary);
                nb.setStyle(s);

                Notification mNotificationObject = nb.build();
                //This is to keep the default settings of notification,
                mNotificationObject.defaults |= Notification.DEFAULT_VIBRATE;
                mNotificationObject.flags |= Notification.FLAG_AUTO_CANCEL;
                //This is to show the ticker text which appear at top.
                mNotificationObject.tickerText = title + "n" + description;
                //Trigger the notification
                NotificationManager manager = (NotificationManager)
                        ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(mNotificationID, mNotificationObject);
            }
        }
    }
}
