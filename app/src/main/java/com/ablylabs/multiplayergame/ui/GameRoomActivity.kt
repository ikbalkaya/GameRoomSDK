package com.ablylabs.pubcrawler.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.ablylabs.ablygamesdk.GamePlayer
import com.ablylabs.ablygamesdk.GameRoom
import com.ablylabs.ablygamesdk.MessageSentResult
import com.ablylabs.ablygamesdk.RoomPresenceResult
import com.ablylabs.ablygamesdk.RoomPresenceUpdate
import com.ablylabs.multiplayergame.MultiplayerGameApp
import com.ablylabs.multiplayergame.MyGamePlayer
import com.ablylabs.multiplayergame.MyGameRoom
import com.ablylabs.multiplayergame.R
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.launch


class GameRoomActivity : AppCompatActivity() {
    private lateinit var playersRecyclerView: RecyclerView
    private lateinit var playersAdapter: PlayersRecyclerAdapter
    private lateinit var room: GameRoom
    private lateinit var player: GamePlayer

    private val viewModel: GameRoomViewModel by viewModels {
        GameRoomViewModelFactory(this, MultiplayerGameApp.instance.ablyGame.roomsController)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_room)
        playersRecyclerView = findViewById(R.id.playersRecyclerView)
        playersAdapter = PlayersRecyclerAdapter(this::sayHiTo)
        setupObservers()
        intent.extras?.let { bundle ->
            bundle.getString(EXTRA_ROOM_JSON)?.let {
                room = Gson().fromJson(it, MyGameRoom::class.java)
                supportActionBar?.title = room.id
                playersRecyclerView.adapter = playersAdapter
            }
            bundle.getString(EXTRA_PLAYER_JSON)?.let {
                player = Gson().fromJson(it, MyGamePlayer::class.java)
                viewModel.enterRoom(player, room)
            }
        }
        findViewById<Button>(R.id.leaveButton).setOnClickListener {
            viewModel.leaveRoom(player, room)
        }

    }

    private fun setupObservers() {
        viewModel.leaveResult.observe(this) { finish() }

        viewModel.enterResult.observe(this) {
            when (it) {
                is RoomPresenceResult.Success -> {
                    lifecycleScope.launch {
                        listenToTheFlow()
                    }
                }
                is RoomPresenceResult.Failure -> {
                    Toast.makeText(this, "Unable to join", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }


        viewModel.messageSentResult.observe(this) {
            when (it) {
                is MessageSentResult.Failed -> Toast.makeText(
                    this, "Couldn't send message to ${it.toWhom.id}", Toast
                        .LENGTH_SHORT
                )
                    .show()
                is MessageSentResult.Success -> Toast.makeText(
                    this, "Message sent to ${it.toWhom.id}", Toast
                        .LENGTH_SHORT
                )
                    .show()
            }
        }

        viewModel.allPlayers.observe(this) {

            supportActionBar?.subtitle = "${it.size} people here"
            playersAdapter.setPlayers(it)
            playersAdapter.notifyDataSetChanged()
        }

        viewModel.presenceActions.observe(this) {
            when (it) {
                is RoomPresenceUpdate.Enter -> someoneJustJoined(it.player)
                is RoomPresenceUpdate.Leave -> someoneJustLeft(it.player)
            }
        }

    }

    private fun listenToTheFlow() {
        viewModel.receivedMessages.observe(this) {
            someoneSentMessage(it.from,it.message)
        }
    }

    private fun someoneSentMessage(who: GamePlayer, message: String) {
        val contentView = findViewById<View>(android.R.id.content)
        Snackbar.make(
            contentView,
            "${who.id} : ${message}",
            Snackbar.LENGTH_LONG
        ).show()
    }



    private fun sayHiTo(to: GamePlayer) {
        viewModel.sendTextMessage(player, to, "Hi \uD83D\uDC4B")
    }

    override fun onBackPressed() {
        viewModel.leaveRoom(player, room)
    }

    private fun someoneJustJoined(
        player: GamePlayer
    ) {
        val contentView = findViewById<View>(android.R.id.content)
        Snackbar.make(
            contentView,
            "${player.id} joined the pub",
            Snackbar.LENGTH_LONG
        ).setAction("Say hi") {
            sayHiTo(player)
        }.show()
    }

    private fun someoneJustLeft(
        player: GamePlayer
    ) {
        val contentView = findViewById<View>(android.R.id.content)
        Snackbar.make(
            contentView,
            "${player.id} left the pub",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    companion object {
        val EXTRA_ROOM_JSON = "EXTRA_ROOM_JSON"
        val EXTRA_PLAYER_JSON = "EXTRA_PLAYER_JSON"
    }
}