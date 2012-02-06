package im.ous.delici.android.contactfix;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

public final class ContactFix extends Activity
{

    public static final String TAG = "ContactFix";

    private Button mDoMagic;

    /**
     * Called when the activity is first created. Responsible for initializing the UI.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "Activity State: onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_fix);
        
        
        WebView webView = (WebView) findViewById(R.id.webView1);
        webView.loadUrl("http://delici.ous.im/?inApp=true");
        
        mDoMagic = (Button) findViewById(R.id.magic);
        
        mDoMagic.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final Cursor contacts = getContacts();
				
				final ProgressDialog progress = new ProgressDialog(ContactFix.this);
				
				progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				progress.setTitle("Processing");
				progress.setMessage("Contact Name");
				progress.setCancelable(false);
				progress.setProgress(0);
				progress.setMax(contacts.getCount());
				progress.show();
				
				
				
				new ProcessContactsTask() {
					@Override
					protected void onProgressUpdate(final ContactFixProgress... values) {
						super.onProgressUpdate(values);
						ContactFixProgress last = values[values.length-1];
						progress.setProgress(last.position);
						progress.setMessage(last.name);
					}
					
					@Override
			    	protected void onPostExecute(Integer result) {
			    		super.onPostExecute(result);
			    		
			    		try {
							contacts.close();
						}
						catch(Exception e) {
							Log.e(TAG,"Cannot close contacts", e);
						}
						
						try {
							progress.dismiss();
						}
						catch(Exception e) {
							Log.e(TAG,"Cannot dismiss notification", e);
						}
						
						try {
							new AlertDialog.Builder(ContactFix.this)
								.setTitle("Processing Done")
								.setMessage(result + " Phone numbers changed.")
								.setNeutralButton(android.R.string.ok, null)
								.show();
						}
						catch(Exception e) {
							Log.e(TAG,"Cannot show end of process notification", e);
						}
			    	}
				}.execute(contacts);
			}

		});

    }
    private static class ContactFixProgress {
    	private final int position;
		private final String name;

		ContactFixProgress(int position, String name) {
			this.position = position;
			this.name = name;
    	}
    }
    class ProcessContactsTask extends AsyncTask<Cursor, ContactFixProgress, Integer> {
    	final ArrayList<ContentProviderOperation> operations;
    	int count;
    	public ProcessContactsTask() {
			operations = new ArrayList<ContentProviderOperation>();
			count = 0;
		}
    	@Override
		protected Integer doInBackground(Cursor... params) {
			Cursor contacts = params[0];
			while(contacts.moveToNext()) {
				String id = contacts.getString(contacts.getColumnIndex(ContactsContract.Contacts._ID));
	        	String name = contacts.getString(contacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
	        	
	        	processContact(id, name);
	        	
	        	if(operations.size() > 30) {
	        		flushOperations();
	        	}
	        	
	        	publishProgress(new ContactFixProgress(contacts.getPosition(),name));
			}
			flushOperations();
			
			return count;
		}
		void flushOperations() {
			if(operations.isEmpty()) {
				return;
			}
			try {
				getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations); //Seriously? you need ArrayList specifically?...
				count += operations.size();
				operations.clear();
			} catch (Exception e) {
				Log.e(TAG, "Could not apply batch",e);
			}
		}
		
		void processContact(String id, String name) {
        	Log.v(TAG,"Contact "+id+", name "+name);
        	
        	Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI , null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?", new String[] {id}, null);
        	while(phones.moveToNext()) {
        		String phoneID = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID));
        		String phoneNumber = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
        		String target = PhoneNumberUtils.formatNumber(phoneNumber);
        		
        		/*if(phoneNumber.equals(target)) {
        			Log.v(TAG,"Phone number already in format: "+phoneNumber);
        			continue;
        		}*/
        		Log.v(TAG,"Fixing Phone Number:::: "+phoneNumber+" to "+target);
        		
        		String where = ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'" +
        						" AND "+ContactsContract.Data._ID+" = ?";
        		
				ContentProviderOperation update = ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
							.withSelection(where, new String[] {phoneID})
							.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, target).build();
				operations.add(update);
        	}
        	phones.close();
		}
    }


    /**
     * Obtains the contact list for the currently selected account.
     *
     * @return A cursor for for accessing the contact list.
     */
    private Cursor getContacts()
    {
        // Run query
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        String[] projection = new String[] {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME
        };
        String selection = null;//ContactsContract.Contacts.HAS_PHONE_NUMBER + "='1'";
        String[] selectionArgs = null;
        String sortOrder = null;//ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

        return managedQuery(uri, projection, selection, selectionArgs, sortOrder);
    }
}
