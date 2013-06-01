package realtalk.activities;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import realtalk.controller.ChatController;
import realtalk.util.ChatManager;
import realtalk.util.ChatRoomInfo;
import realtalk.util.CommonUtilities;
import realtalk.util.Emoticonifier;
import realtalk.util.MessageInfo;
import realtalk.util.PullMessageResultSet;
import realtalk.util.RequestResultSet;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import android.widget.Toast;

import com.realtalk.R;

/**
 * Activity for a chat room, where the user can send/recieve messages.
 * 
 * @author Jordan Hazari 
 *
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ChatRoomActivity extends Activity {
	private ChatRoomInfo chatroominfo;
	private UserInfo userinfo;
	private ProgressDialog progressdialog;
	private List<MessageInfo> rgmi = new ArrayList<MessageInfo>();
	private MessageAdapter adapter;
	private ChatController chatController = ChatController.getInstance();
	private SoundPool soundpool;
	private int iMessageBeep = 0;
	
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
		userinfo = ChatController.getInstance().getUser();
		chatroominfo = extras.getParcelable("ROOM");
		
		String stUser = userinfo.stUserName();
		String stRoom = chatroominfo.stName();
		
		TextView textviewRoomTitle = (TextView) findViewById(R.id.chatRoomTitle);
		textviewRoomTitle.setText(stRoom);
		TextView textviewUserTitle = (TextView) findViewById(R.id.userTitle);
		textviewUserTitle.setText(stUser);
		
		soundpool = new SoundPool(2, AudioManager.STREAM_NOTIFICATION, 0);
		iMessageBeep = soundpool.load(getApplicationContext(), R.raw.messagebeep, 1);
		
		new RoomCreator(this, chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

		ListView listview = (ListView) findViewById(R.id.list);
		adapter = new MessageAdapter(this, R.layout.message_item, rgmi);
		listview.setAdapter(adapter);
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
        itViewRooms.putExtra("USER", userinfo);
		this.startActivity(itViewRooms);
		this.finish();

    }
	
	public void leaveRoom(View view) {
		new RoomLeaver(chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}
	
	/**
	 * Method that loads messages to adapter. Prepares the chat view to use GCM thereafter.
	 */
	public void createGCMMessageLoader() {
	    new GCMMessageLoader(this, chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
			MessageInfo message = new MessageInfo (stValue, userinfo.stUserName(), new Timestamp(System.currentTimeMillis()));
			
			new MessageSender(message, chatroominfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			edittext.setText("");
		}
	}
	
	/**
	 * AsyncTask that leaves the room.
	 * 
	 * @author Colin Kho
	 *
	 */
	class RoomLeaver extends AsyncTask<String, String, Boolean> {
	    private ChatRoomInfo chatroominfo;
	    public RoomLeaver(ChatRoomInfo roominfo) {
	        chatroominfo = roominfo;
	    }
	    
	    @Override
	    protected void onPreExecute() {
	        super.onPreExecute();
            progressdialog = new ProgressDialog(ChatRoomActivity.this);
            progressdialog.setMessage("Leaving room. Please wait...");
            progressdialog.setIndeterminate(false);
            progressdialog.setCancelable(true);
            progressdialog.show();
	    }
	    
	    /**
	     * @return Boolean with whether the room was left successfully.
	     */
        @Override
        protected Boolean doInBackground(String... params) {
        	Log.d("connectivitiy", "Arrived in ChatRoomActivity.RoomLeaver.doInBackground");
            ConnectivityManager connectivitymanager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Log.d("connectivitiy", "Accessed ConnectivityManager");
            NetworkInfo networkinfo = connectivitymanager.getActiveNetworkInfo();
            Log.d("connectivitiy", "Accessed network info");
            
			if (networkinfo != null && networkinfo.isConnected()) {
				Log.d("connectivitiy", "Attempting to leave room");
				Boolean fLeaveRoom = chatController.leaveRoom(chatroominfo);
				return fLeaveRoom;
			} else {
				Log.d("connectivitiy", "disconnected, leaving doInBackground");
				return false;
			}
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (progressdialog != null) {
                progressdialog.dismiss();
            }
            
            if (success == false) {
            	Toast toast = Toast.makeText(getApplicationContext(), R.string.leave_room_failed, Toast.LENGTH_SHORT);
				toast.show();
            } else {
		        Intent itViewRooms = new Intent(ChatRoomActivity.this, SelectRoomActivity.class);
		        itViewRooms.putExtra("USER", userinfo);
		  		ChatRoomActivity.this.startActivity(itViewRooms);
		  		ChatRoomActivity.this.finish();
            }
            Log.d("connectivity", "Leaving RoomLeaver asynctask");
        }    
	}
	
	/**
	 * Posts a message to the room's database
	 * 
	 * @author Jordan Hazari
	 *
	 */
	class MessageSender extends AsyncTask<String, String, RequestResultSet> {
		private MessageInfo messageinfo;
		private ChatRoomInfo chatroominfo;
		
		/**
		 * Constructs a MessageSender object
		 * 
		 * @param message the message to be sent
		 * @param chatroominfo the room to post the message to
		 */
		public MessageSender(MessageInfo message, ChatRoomInfo chatroominfo) {
			this.messageinfo = message;
			this.chatroominfo = chatroominfo;
		}

		/**
		 * Posts the message to the room
		 * 
		 * @return null if disconnected from network
		 */
		@Override
		protected RequestResultSet doInBackground(String... params) {
			ConnectivityManager connectivitymanager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkinfo = connectivitymanager.getActiveNetworkInfo();
			if (networkinfo != null && networkinfo.isConnected()) {
				Log.d("connectivity", "Connected and attempting to post message");
				RequestResultSet rrs =ChatManager.rrsPostMessage(userinfo, chatroominfo, messageinfo);
				Log.d("connectivity", "Message posted");
				return rrs;
			} else {
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(RequestResultSet rrs) {
			if (rrs == null) {
				Toast toast = Toast.makeText(getApplicationContext(), R.string.network_failed, Toast.LENGTH_LONG);
				toast.show();
			}
		}
	}
	
//	/**
//	 * Retrieves the message log of a chat room
//	 * 
//	 * @author Jordan Hazari
//	 *
//	 */
//	class MessageLoader extends AsyncTask<String, String, PullMessageResultSet> {
//		private ChatRoomActivity chatroomactivity;
//		private ChatRoomInfo chatroominfo;
//		private static final int RECENT_MESSAGE_TIME_LIMIT = 1000000000;
//		
//		/**
//		 * Constructs a MessageLoader object
//		 * 
//		 * @param chatroomactivity the activity context
//		 * @param chatroominfo the chat room to retrieve the chat log from
//		 */
//		public MessageLoader(ChatRoomActivity chatroomactivity, ChatRoomInfo chatroominfo) {
//			this.chatroomactivity = chatroomactivity;
//			this.chatroominfo = chatroominfo;
//		}
//
//		/**
//		 * Retrieves and displays the chat log, constantly updating
//		 */
//		@Override
//		protected PullMessageResultSet doInBackground(String... params) {
//			while (true) {
//				PullMessageResultSet pmrsRecent = ChatManager.pmrsChatRecentChat
//						(chatroominfo, new Timestamp(System.currentTimeMillis()-RECENT_MESSAGE_TIME_LIMIT));
//				
//				rgmi = pmrsRecent.getRgmessage();
//				
//				chatroomactivity.runOnUiThread(new Runnable() {
//					@Override
//					public void run() {
//						adapter.clear();
//						
//						for (int i = 0; i < rgmi.size(); i++) {
//							adapter.add(rgmi.get(i));
//						}
//					}
//				});
//			}
//		}
//	}
	
	/**
	 * Loads messages from chat controller which is prepared for GCM.
	 * 
	 * @author Colin Kho
	 *
	 */
	class GCMMessageLoader extends AsyncTask<String, String, Boolean> {
	    private Activity activity;
	    private ChatRoomInfo chatroominfo;
	    
	    public GCMMessageLoader(Activity activity, ChatRoomInfo chatroominfo) {
	        this.activity = activity;
	        this.chatroominfo = chatroominfo;
	    }
        @Override
        protected Boolean doInBackground(String... params) {
            rgmi = chatController.getMessagesFromChatRoom(chatroominfo.stId());
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.clear();
                    
                    for (int i = 0; i < rgmi.size(); i++) {
                        adapter.add(rgmi.get(i));
                    }
                }
            });
            return true;
        }	    
	}
	
	/**
	 * Joins a chat room
	 * 
	 * @author Jordan Hazari
	 *
	 */
	class RoomCreator extends AsyncTask<String, String, Boolean> {
		private ChatRoomInfo chatroominfo;
		private Activity activity;
		
		/**
		 * Constructs a RoomCreator object
		 * 
		 * @param chatroominfo the room to create/join
		 */
		public RoomCreator(Activity activity, ChatRoomInfo chatroominfo) {
			this.chatroominfo = chatroominfo;
			this.activity = activity;
		}
		
		/**
		 * Displays a popup dialogue while joining the room
		 */
	    @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressdialog = new ProgressDialog(ChatRoomActivity.this);
            progressdialog.setMessage("Entering room. Please wait...");
            progressdialog.setIndeterminate(false);
            progressdialog.setCancelable(true);
            progressdialog.setCanceledOnTouchOutside(false);
            progressdialog.show();
        }

	    /**
	     * Adds the room, or joins it if it already exists
	     * @return 
	     */
		@Override
		protected Boolean doInBackground(String... params) {
			ChatController chatcontroller = ChatController.getInstance();
			if (chatcontroller.fIsAlreadyJoined(chatroominfo)) {
				return true;
			} else {
				ConnectivityManager connectivitymanager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	            NetworkInfo networkinfo = connectivitymanager.getActiveNetworkInfo();
	            if (networkinfo != null && networkinfo.isConnected()) {
					return chatcontroller.joinRoom(chatroominfo);
				}
	            return null;
			}
		}
		
		/**
		 * Closes the popup dialogue
		 */
		@Override
        protected void onPostExecute(Boolean success) {
            progressdialog.dismiss();
            if (success == null) {
            	Toast toast = Toast.makeText(getApplicationContext(), R.string.network_failed, Toast.LENGTH_LONG);
    			toast.show();
            } else if (!success) {
                Toast serverToast = Toast.makeText(activity, R.string.join_room_failed, Toast.LENGTH_LONG);
                serverToast.show();
            } else {
                ChatRoomActivity.this.createGCMMessageLoader();
            }
		}
	}
	
	private class MessageAdapter extends ArrayAdapter<MessageInfo> {

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
                	SimpleDateFormat simpledateformat = new SimpleDateFormat("hh:mm a, M/dd/yyyy", Locale.US);
                	simpledateformat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
                    textviewBottom.setText("\t" + simpledateformat.format(mi.timestampGet()));
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
                    List<MessageInfo> rgmessageinfo = chatController.getMessagesFromChatRoom(stRoomId);
                    
                    // Update adapter to have new messages.
                    adapter.clear();
                    
                    for (int iMsgIndex = 0; iMsgIndex < rgmessageinfo.size(); iMsgIndex++) {
                        adapter.add(rgmessageinfo.get(iMsgIndex));
                    }
                    
                    //play sound here TODO
                    soundpool.play(iMessageBeep, .1f, .1f, 1, 0, 1f);
                }    
	};
}
