package realtalk.activities;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import realtalk.asynctasks.GCMMessageLoader;
import realtalk.asynctasks.MessageSender;
import realtalk.asynctasks.RoomJoiner;
import realtalk.asynctasks.RoomLeaverFromRoom;
import realtalk.controller.ChatController;
import realtalk.controller.ChatControllerStub;
import realtalk.controller.IChatController;
import realtalk.util.ChatRoomInfo;
import realtalk.util.CommonUtilities;
import realtalk.util.Emoticonifier;
import realtalk.util.MessageInfo;
import realtalk.util.UserInfo;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.realtalk.R;

/**
 * Activity for a chat room, where the user can send/recieve messages.
 * 
 * @author Jordan Hazari 
 *
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ChatRoomActivity extends Activity {
    // Sound constants
    private static final float LEFT_VOL = .1f;
    private static final float RIGHT_VOL = .1f;
    private static final float RATE = 1f;
    private static final int PRIORITY = 1;
    private static final int LOOP = 0;
    
	private ChatRoomInfo chatroominfo;
	private UserInfo userinfo;
	private ProgressDialog progressdialog;
	private List<MessageInfo> rgmi = new ArrayList<MessageInfo>();
	private MessageAdapter adapter;
	private IChatController chatController;
	private Boolean fAnon;
	private SoundPool soundpool;
	private int iMessageBeep = 0;
	private boolean fDebug;
	
	/**
	 * Sets up the chat room activity and loads the previous
	 * messages from the chat room
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_chat_room);
		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		Bundle extras = getIntent().getExtras();
		chatroominfo = extras.getParcelable("ROOM");
		boolean fLocalDebug = extras.getBoolean("DEBUG");
		
		if(!fLocalDebug) {
			setChatController(ChatController.getInstance());
		    setfDebug(false);   
		} else {
			setChatController(ChatControllerStub.getInstance());
			setfDebug(true);
		}
		setUserinfo(getChatController().getUser());

		setfAnon(extras.getBoolean("ANON", false));
		
		String stUser = getUserinfo().stUserName();
		String stRoom = chatroominfo.stName();
		
		TextView textviewRoomTitle = (TextView) findViewById(R.id.chatRoomTitle);
		textviewRoomTitle.setText(stRoom);
		TextView textviewUserTitle = (TextView) findViewById(R.id.userTitle);
		textviewUserTitle.setText(stUser);
		
		soundpool = new SoundPool(2, AudioManager.STREAM_NOTIFICATION, 0);
		iMessageBeep = soundpool.load(getApplicationContext(), R.raw.messagebeep, 1);
		
		new RoomJoiner(this, this, chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		ListView listview = (ListView) findViewById(R.id.list);
		setAdapter(new MessageAdapter(this, R.layout.message_item, rgmi));
		listview.setAdapter(getAdapter());
	}
	
	@Override
	protected void onStart() {
	    super.onStart();
	    registerReceiver(handleNewMessageAlertReceiver,
	            new IntentFilter(CommonUtilities.NEW_MESSAGE_ALERT));
	}
	
	@Override
	protected void onResume() {
	    super.onResume();
	    registerReceiver(handleNewMessageAlertReceiver,
                new IntentFilter(CommonUtilities.NEW_MESSAGE_ALERT));
	}
	
	@Override
	protected void onPause() {
	    super.onPause();
	    unregisterReceiver(handleNewMessageAlertReceiver);
	}
	
	@Override
	protected void onRestart() {
	    super.onRestart();
	    registerReceiver(handleNewMessageAlertReceiver,
                new IntentFilter(CommonUtilities.NEW_MESSAGE_ALERT));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.chat_room, menu);
		return true;
	}
	
	@Override
    public void onBackPressed() { 
        Intent itViewRooms = new Intent(this, SelectRoomActivity.class);
		this.startActivity(itViewRooms);
		this.finish();

    }
	
	public void leaveRoom(View view) {
		new RoomLeaverFromRoom(this, chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	/**
	 * Method that loads messages to adapter. Prepares the chat view to use GCM thereafter.
	 */
	public void createGCMMessageLoader() {
	    new GCMMessageLoader(this, this, chatroominfo, chatController).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	/**
	 * Creates and sends a message typed by the user.
	 * 
	 * @param view
	 */
	public void createMessage(View view) {
		EditText edittext = (EditText)findViewById(R.id.message);
		String stValue = edittext.getText().toString();
		
		if (!stValue.equals("")) {
			MessageInfo message = new MessageInfo (stValue, getUserinfo().stUserName(), new Timestamp(System.currentTimeMillis()));
			new MessageSender(this, message, chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			edittext.setText("");
		}
	}
	
	public class MessageAdapter extends ArrayAdapter<MessageInfo> {
        private List<MessageInfo> rgmi;
        
        public MessageAdapter(Context context, int textViewResourceId, List<MessageInfo> rgmessageinfo) {
            super(context, textViewResourceId, rgmessageinfo);
            this.rgmi = rgmessageinfo;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.message_item, null);
            }
            MessageInfo mi = rgmi.get(position);
            if (mi != null) {
                TextView textviewTop = (TextView) convertView.findViewById(R.id.toptext);
                TextView textviewBottom = (TextView) convertView.findViewById(R.id.bottomtext);
                if (textviewTop != null) {
                	textviewTop.setTextAppearance(ChatRoomActivity.this, android.R.style.TextAppearance_Medium);
                	
                	SpannableStringBuilder ssbSender = new SpannableStringBuilder(mi.stSender()+": ");
                	ssbSender.setSpan(new StyleSpan(Typeface.BOLD), 0, ssbSender.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                	Spannable stBody = Emoticonifier.getSmiledText(getApplicationContext(), mi.stBody());
                	Spanned stTopText = (Spanned) TextUtils.concat(ssbSender, stBody);
                	textviewTop.setText(stTopText, BufferType.SPANNABLE);
                }
                if (textviewBottom != null) {
                    textviewBottom.setText("\t" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(mi.timestampGet()));
                }
            }
            return convertView;
        }
	}
	
	private final BroadcastReceiver handleNewMessageAlertReceiver = 
	        new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.v("Handling Message Receiver", "Received Message");
                    String stRoomId = intent.getExtras().getString(CommonUtilities.ROOM_ID);
                    
                    // Retrieve messages from controller.
                    List<MessageInfo> rgmessageinfo = getChatController().getMessagesFromChatRoom(stRoomId);
                    
                    // Update adapter to have new messages.
                    getAdapter().clear();
                    
                    for (int iMsgIndex = 0; iMsgIndex < rgmessageinfo.size(); iMsgIndex++) {
                        getAdapter().add(rgmessageinfo.get(iMsgIndex));
                    }
                    
                    soundpool.play(iMessageBeep, LEFT_VOL, RIGHT_VOL, PRIORITY, LOOP, RATE);
                }    
	};
	
	/**
	 * Used for debugging
	 */
	public void populateAdapter(final List<MessageInfo> rgmessageinfo) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {   
            	getAdapter().clear();
        		for(MessageInfo messageinfo : rgmessageinfo) {
        			getAdapter().add(messageinfo);
        		}
            }
        });
	}

    /**
     * @return the adapter
     */
    public MessageAdapter getAdapter() {
        return adapter;
    }

    /**
     * @param adapter the adapter to set
     */
    public void setAdapter(MessageAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * @return the chatController
     */
    public IChatController getChatController() {
        return chatController;
    }

    /**
     * @param chatController the chatController to set
     */
    public void setChatController(IChatController chatController) {
        this.chatController = chatController;
    }

    /**
     * @return the userinfo
     */
    public UserInfo getUserinfo() {
        return userinfo;
    }

    /**
     * @param userinfo the userinfo to set
     */
    public void setUserinfo(UserInfo userinfo) {
        this.userinfo = userinfo;
    }

    /**
     * @return the progressdialog
     */
    public ProgressDialog getProgressdialog() {
        return progressdialog;
    }

    /**
     * @param progressdialog the progressdialog to set
     */
    public void setProgressdialog(ProgressDialog progressdialog) {
        this.progressdialog = progressdialog;
    }

    /**
     * @return the fAnon
     */
    public Boolean getfAnon() {
        return fAnon;
    }

    /**
     * @param fAnon the fAnon to set
     */
    public void setfAnon(Boolean fAnon) {
        this.fAnon = fAnon;
    }

    /**
     * @return the fDebug
     */
    public boolean isfDebug() {
        return fDebug;
    }

    /**
     * @param fDebug the fDebug to set
     */
    public void setfDebug(boolean fDebug) {
        this.fDebug = fDebug;
    }
}
