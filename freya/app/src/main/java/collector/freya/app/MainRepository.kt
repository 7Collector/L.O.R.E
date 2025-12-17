package collector.freya.app

import androidx.paging.Pager
import androidx.paging.PagingConfig
import collector.freya.app.database.chats.ChatsDao
import javax.inject.Inject

class MainRepository @Inject constructor(
    private val chatsDao: ChatsDao,
) {

    fun getChats() = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false
        ),
        pagingSourceFactory =
            { chatsDao.pagingSource() }
    ).flow
}