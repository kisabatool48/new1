package com.example.contact_page

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 1. Data Model
data class Contact(
    val name: String,
    val number: String,
    val initial: String,
    val colorHex: String,
    val isPrimary: Boolean
)

class MainActivity : AppCompatActivity() {

    private val contacts = mutableListOf<Contact>()
    private lateinit var adapter: ContactsAdapter
    private val PREFS_NAME = "TrustedContactsPrefs"
    private val KEY_CONTACTS = "contacts_list"

    // Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchContactPicker()
        } else {
            Toast.makeText(this, "Permission denied to read contacts", Toast.LENGTH_SHORT).show()
        }
    }

    // Contact Picker Launcher
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            // Query the contact details
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    val id = if (idIndex >= 0) it.getString(idIndex) else null
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val hasPhone = if (hasPhoneIndex >= 0) it.getInt(hasPhoneIndex) else 0

                    if (hasPhone > 0 && id != null) {
                        // Query for phone number
                        val phones = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        phones?.use { pCursor ->
                            if (pCursor.moveToFirst()) {
                                val numberIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val number = if (numberIndex >= 0) pCursor.getString(numberIndex) else ""

                                if (number.isNotEmpty()) {
                                    addContactToList(name, number)
                                } else {
                                    Toast.makeText(this, "No phone number found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Selected contact has no phone number", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trusted_contacts)

        // Setup RecyclerView
        val rvContacts = findViewById<RecyclerView>(R.id.rvContacts)
        rvContacts.layoutManager = LinearLayoutManager(this)

        // Load Contacts
        loadContacts()

        // Initialize Adapter with Add Click Callback and Delete Callback
        adapter = ContactsAdapter(contacts, {
            checkPermissionAndPickContact()
        }, { contact ->
            deleteContact(contact)
        })
        rvContacts.adapter = adapter

        // Setup Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_contacts
    }

    private fun checkPermissionAndPickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        pickContactLauncher.launch(null)
    }

    private fun addContactToList(name: String, number: String) {
        val initial = if (name.isNotEmpty()) name.first().toString().uppercase() else "?"
        val colorHex = getRandomColor()
        val newContact = Contact(name, number, initial, colorHex, false)
        
        contacts.add(newContact)
        adapter.notifyDataSetChanged()
        saveContacts()
    }

    private fun deleteContact(contact: Contact) {
        val position = contacts.indexOf(contact)
        if (position != -1) {
            contacts.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, contacts.size)
            saveContacts()
        }
    }

    private fun getRandomColor(): String {
        val colors = listOf(
            "#F48FB1", // Pink
            "#90CAF9", // Blue
            "#CE93D8", // Purple
            "#FFCC80", // Orange
            "#80CBC4", // Teal
            "#B39DDB"  // Deep Purple
        )
        return colors.random()
    }

    private fun saveContacts() {
        val sharedPreferences: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(contacts)
        editor.putString(KEY_CONTACTS, json)
        editor.apply()
    }

    private fun loadContacts() {
        val sharedPreferences: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString(KEY_CONTACTS, null)
        val type = object : TypeToken<MutableList<Contact>>() {}.type

        if (json != null) {
            val savedContacts: MutableList<Contact> = gson.fromJson(json, type)
            contacts.clear()
            contacts.addAll(savedContacts)
        } else {
            // Initial Mock Data if no data saved
            contacts.clear()
            contacts.addAll(listOf(
                Contact("Mom", "+1 (555) 123-4567", "M", "#F48FB1", true),
                Contact("Sarah Johnson", "+1 (555) 234-5678", "S", "#90CAF9", false),
                Contact("Dad", "+1 (555) 345-6789", "D", "#CE93D8", false)
            ))
            saveContacts() // Save default data for next time
        }
    }
}

// 2. Adapter
class ContactsAdapter(
    private val contacts: List<Contact>,
    private val onAddClick: () -> Unit,
    private val onDeleteClick: (Contact) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_CONTACT = 0
    private val TYPE_ADD_BUTTON = 1

    override fun getItemViewType(position: Int): Int {
        return if (position < contacts.size) TYPE_CONTACT else TYPE_ADD_BUTTON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_CONTACT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
            return ContactViewHolder(view)
        } else {
            // Inflate layout for the "Add Contact" button
            val view = LayoutInflater.from(parent.context).inflate(R.layout.add_contact, parent, false)
            return AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ContactViewHolder) {
            val contact = contacts[position]
            holder.tvName.text = contact.name
            holder.tvNumber.text = contact.number
            holder.tvInitial.text = contact.initial

            // Set Badge Visibility
            holder.tvBadge.visibility = if (contact.isPrimary) View.VISIBLE else View.GONE

            // Set Avatar Color
            try {
                holder.cardAvatar.setCardBackgroundColor(android.graphics.Color.parseColor(contact.colorHex))
            } catch (e: Exception) {
                // Fallback
                holder.cardAvatar.setCardBackgroundColor(android.graphics.Color.LTGRAY)
            }

            // Setup More/Menu Button
            holder.btnMore.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add("Delete")
                popup.setOnMenuItemClickListener { item ->
                    if (item.title == "Delete") {
                        onDeleteClick(contact)
                        true
                    } else {
                        false
                    }
                }
                popup.show()
            }

        } else if (holder is AddViewHolder) {
            holder.itemView.setOnClickListener {
                onAddClick()
            }
        }
    }

    override fun getItemCount(): Int = contacts.size + 1 // +1 for the Add Button

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvName)
        val tvNumber: TextView = itemView.findViewById(R.id.tvNumber)
        val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
        val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)
        val cardAvatar: CardView = itemView.findViewById(R.id.cardAvatar)
        val btnMore: ImageView = itemView.findViewById(R.id.btnMore)
    }

    class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
