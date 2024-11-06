package com.undefinedus.backend.service;

import com.undefinedus.backend.domain.entity.AladinBook;
import com.undefinedus.backend.domain.entity.Book;
import com.undefinedus.backend.domain.entity.CalendarStamp;
import com.undefinedus.backend.domain.entity.Member;
import com.undefinedus.backend.domain.enums.BookStatus;
import com.undefinedus.backend.dto.request.book.BookStatusRequestDTO;
import com.undefinedus.backend.repository.AladinBookRepository;
import com.undefinedus.backend.repository.BookRepository;
import com.undefinedus.backend.repository.CalendarStampRepository;
import com.undefinedus.backend.repository.MemberRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Log4j2
@Transactional
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;

    private final AladinBookRepository aladinBookRepository;

    private final MemberRepository memberRepository;

    private final CalendarStampRepository calendarStampRepository;

    private final ModelMapper modelMapper;


    @Override
    public boolean existsBook(Long memberId, String isbn13) {

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new UsernameNotFoundException("해당 member를 찾을 수 없습니다. : " + memberId));

        return bookRepository.findByMemberIdAndIsbn13(member.getId(), isbn13).isPresent();

    }

    @Override
    public void insertNewBookByStatus(Long memberId, String tabCondition, AladinBook savedAladinBook,
        BookStatusRequestDTO requestDTO) {
        try {

            Member findMember = getLoginMember(memberId);

            System.out.println("findMember = " + findMember);

            Book book = Book.builder()
                .member(findMember)
                .aladinBook(savedAladinBook)
                .isbn13(savedAladinBook.getIsbn13())
                .build();

            // TODO : 나중 QueryDSL 적용하면 바꿀지 말지 고민필요
            saveBookAndCalenarStampByStatus(tabCondition, requestDTO, book, savedAladinBook, findMember);
        } catch (UsernameNotFoundException e) {
            log.error("사용자를 찾을 수 없습니다. : ", e);
            throw new RuntimeException("사용자 인증에 실패했습니다.", e);
        } catch (Exception e) {
            log.error("에러가 발생했습니다. : ", e);
            throw new RuntimeException("사용자 인증에 실패했습니다.", e);
        }

    }

    private void saveBookAndCalenarStampByStatus(String tabCondition,
        BookStatusRequestDTO requestDTO, Book book,
        AladinBook findAladinBook, Member findMember) {
        if (BookStatus.COMPLETED.name().equals(tabCondition.toUpperCase())) {
            book = saveBookWithCompletedStatus(requestDTO, book, findAladinBook);
            recordCalendarStamp(findMember, book);
        } else if (BookStatus.READING.name().equals(tabCondition.toUpperCase())) {
            book = saveBookWithReadingStatus(requestDTO, book);
            recordCalendarStamp(findMember, book);
        } else if (BookStatus.WISH.name().equals(tabCondition.toUpperCase())) {
            book = saveBookWithWishStatus(book);
            recordCalendarStamp(findMember, book);
        } else if (BookStatus.STOPPED.name().equals(tabCondition.toUpperCase())) {
            book = saveBookWithStoppedStatus(requestDTO, book);
            recordCalendarStamp(findMember, book);
        } else {
            throw new IllegalArgumentException("알맞은 status값이 들어와야 합니다. : " + tabCondition);
        }
    }

    private Book saveBookWithStoppedStatus(BookStatusRequestDTO requestDTO, Book book) {
        book = book.toBuilder()
            .status(BookStatus.STOPPED)
            .myRating(requestDTO.getMyRating())
            .oneLineReview(requestDTO.getOneLineReview())
            .currentPage(requestDTO.getCurrentPage())
            .startDate(requestDTO.getStartDate())
            .finishDate(requestDTO.getFinishDate())
            .build();

        bookRepository.save(book);
        return book;
    }

    private Book saveBookWithWishStatus(Book book) {
        book = book.toBuilder()
            .status(BookStatus.WISH)
            .build();

        bookRepository.save(book);
        return book;
    }

    private Book saveBookWithReadingStatus(BookStatusRequestDTO requestDTO, Book book) {
        book = book.toBuilder()
            .status(BookStatus.READING)
            .myRating(requestDTO.getMyRating())
            .currentPage(requestDTO.getCurrentPage())
            .startDate(LocalDate.now())
            .build();

        bookRepository.save(book);
        return book;
    }

    private Book saveBookWithCompletedStatus(BookStatusRequestDTO requestDTO, Book book,
        AladinBook findAladinBook) {
        book = book.toBuilder()
            .status(BookStatus.COMPLETED)
            .myRating(requestDTO.getMyRating())
            .oneLineReview(requestDTO.getOneLineReview())
            .currentPage(findAladinBook.getPagesCount()) // 다 읽었으니 100%로 만들기 위해
            .startDate(requestDTO.getStartDate())
            .finishDate(requestDTO.getFinishDate())
            .build();

        bookRepository.save(book);
        return book;
    }

    private void recordCalendarStamp(Member findMember, Book book) {
        CalendarStamp calendarStamp = CalendarStamp.builder()
            .member(findMember)
            .book(book)
            .recordDate(LocalDate.now())
            .status(book.getStatus())
            .build();

        calendarStampRepository.save(calendarStamp);
    }

    private Member getLoginMember(Long memberId) {

        Member findMember = memberRepository.findById(memberId)
            .orElseThrow(
                () -> new UsernameNotFoundException("해당 member를 찾을 수 없습니다. : " + memberId));

        log.info("찾아온 member 정보: " + findMember);

        return findMember;
    }
}
