package com.arshaa.service;

import com.arshaa.common.Bed;
import com.arshaa.common.Payment;
import com.arshaa.dtos.GuestDto;
import com.arshaa.entity.Guest;
import com.arshaa.model.GuestsInNotice;
import com.arshaa.model.PreviousGuests;
import com.arshaa.model.VacatedGuests;
import com.arshaa.repository.GuestRepository;


import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Service
public class GuestService implements GuestInterface {
    @Autowired(required = true)
    private GuestRepository repository;

    @Autowired
    @Lazy
    private RestTemplate template;
    
    @Autowired
    private ModelMapper  modelMapper ;
    
    @Autowired
	@PersistenceContext
	private EntityManager em;

   @Override
	public List<GuestDto> getGuests(String field) {
		List<Guest> getGuest = repository.findAll(Sort.by(Sort.Direction.DESC, field));
		List<GuestDto> gdto = new ArrayList<>();

		getGuest.forEach(s -> {
			GuestDto d = new GuestDto();
			d.setAadharNumber(s.getAadharNumber());
			d.setBedId(s.getBedId());
			d.setBuildingId(s.getBuildingId());
			d.setGuestName(s.getFirstName().concat(" ").concat(s.getLastName()));
			d.setAmountPaid(s.getAmountPaid());
			d.setBuildingId(s.getBuildingId());
			String name = template.getForObject(
					"http://bedService/bed/getBuildingNameByBuildingId/" + s.getBuildingId(), String.class);
			d.setBuildingName(name);
			d.setPersonalNumber(s.getPersonalNumber());
			d.setCheckInDate(s.getCheckInDate());
			d.setCheckOutDate(s.getCheckOutDate());
			d.setAddressLine1(s.getAddressLine1());
			d.setAddressLine2(s.getAddressLine2());
			d.setId(s.getId());
			d.setDefaultRent(s.getDefaultRent());
			gdto.add(d);

		});
		return gdto;
	}

    @Override
    public Guest getGuestById(String guestId) {
        return repository.findById(guestId);
    }

    @Override
    public Guest addGuest(Guest guest) {
        //double initialDefaultrent = 0;
        String bedUri = "http://bedService/bed/updateBedStatusBydBedId";
        String payUri = "http://paymentService/payment/addPaymentAtOnBoarding";
 //     Bed getUniqueBed = template.getForObject("http://bedService/bed/getBedByBedId/" + guest.getBedId(), Bed.class);
//        if (getUniqueBed.getBedId().equalsIgnoreCase(guest.getBedId())) {
//            System.out.println(getUniqueBed.getBedId());
//            guest.setDueAmount(getUniqueBed.getDefaultRent() - guest.getAmountPaid());
//        }
       // SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        //System.out.println(formatter.format(tSqlDate));
       
        java.sql.Date tSqlDate = new java.sql.Date(guest.getTransactionDate().getTime());
        
        guest.setTransactionDate(tSqlDate);
        
        java.sql.Date cSqlDate = new java.sql.Date(guest.getCheckInDate().getTime());
        
       guest.setCheckInDate(cSqlDate);
       java.sql.Date createDate =new java.sql.Date(guest.getCreatedOn().getTime());
       guest.setCreatedOn(createDate);
       
        repository.save(guest);
        
        if(guest.getOccupancyType().equalsIgnoreCase("daily"))
        {
        	java.util.Date m = guest.getCheckInDate();
            Calendar cal = Calendar.getInstance();  
            cal.setTime(m);  
            cal.add(Calendar.DATE, guest.getDuration()); 
            m = cal.getTime();   
            System.out.println(m);
            guest.setPlannedCheckOutDate(m);
            guest.setGuestStatus("active");            
            repository.save(guest);
        }
        else if(guest.getOccupancyType().equalsIgnoreCase("monthly"))
        {
        	java.util.Date m = guest.getCheckInDate();
            Calendar cal = Calendar.getInstance();  
            cal.setTime(m);  
            cal.add(Calendar.MONTH, guest.getDuration()); 
            m = cal.getTime();   
            System.out.println(m);
            
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
            //System.out.println(dtf.format(m));  

            guest.setPlannedCheckOutDate(m);
            guest.setGuestStatus("active");            
            repository.save(guest);
        }        
        else {
            guest.setGuestStatus("active");            

            repository.save(guest);
        }


//        System.out.println(initialDefaultrent); 
        guest.setGuestStatus("active");            

        repository.save(guest);
                System.out.println(guest.getDueAmount());
        Bed bedReq = new Bed();
        Payment payReq = new Payment();
        //bed setting
        bedReq.setBedId(guest.getBedId());
        
        bedReq.setGuestId(guest.getId());
        //bedReq.setDueAmount(guest.getDueAmount());
        template.put(bedUri, bedReq, Bed.class);
        //payment setting
        payReq.setGuestId(guest.getId());
        payReq.setBuildingId(guest.getBuildingId());
        payReq.setTransactionId(guest.getTransactionId());
        payReq.setOccupancyType(guest.getOccupancyType());
        payReq.setTransactionDate(tSqlDate);
       // payReq.setCheckinDate(cSqlDate);
        payReq.setAmountPaid(guest.getAmountPaid());
       // payReq.setDueAmount(guest.getDueAmount());
        payReq.setPaymentPurpose(guest.getPaymentPurpose());
        repository.save(guest);
        Payment parRes = template.postForObject(payUri, payReq, Payment.class);
        System.out.println(parRes);
                return guest;
    }

    @Override
    public double updateGuest(Guest guest) {
        Guest newGuest = repository.findById(guest.getId());
        newGuest.setDueAmount(guest.getDueAmount());
        repository.save(newGuest);
        return newGuest.getDueAmount();
    }

    @Override
    public void deleteGuest(String guestId) {
        Guest deleteGuest = repository.findById(guestId);
        repository.delete(deleteGuest);
    }
    
 // Method to fetch the dueamount by guestId .
 	@SuppressWarnings("unchecked")
 	@Override
 	public List<Guest> getByGuestId(String guestId) {
 		// TODO Auto-generated method stub

 		return em.createNamedStoredProcedureQuery("firstProcedure").setParameter("g_id", guestId).getResultList();

 	}

      //Method to fetch all the dueamount .
 	@SuppressWarnings("unchecked")
 	@Override
 	public List<Guest> getTotalDue() {

 		return em.createNamedStoredProcedureQuery("dueDashBoard").getResultList();

 	}

	@Override
	public List<Guest> getPendingByBuildingId(int buildingId) {
		// TODO Auto-generated method stub
		return em.createNamedStoredProcedureQuery("thirdProcedure").setParameter("b_id", buildingId).getResultList();


	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public List<Guest> getCheckOutAmountByGuestId(String id) {
	// TODO Auto-generated method stub
	return em.createNamedStoredProcedureQuery("checkOut").setParameter("GUEST__ID" , id).getResultList();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Guest> getFinalDueAmountById(String id) {
		// TODO Auto-generated method stub
		return em.createNamedStoredProcedureQuery("finalDue").setParameter("guest__id" , id).getResultList();
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Guest> getOnlyDues(String id) {
		// TODO Auto-generated method stub
		return em.createNamedStoredProcedureQuery("onlyDues").setParameter("GUEST__ID" , id).getResultList();
	}

	

	public List<VacatedGuests> findByGuestStatus(String guestStatus)
   	{

		List<Guest> getList = repository.findByGuestStatus(guestStatus);
		List<VacatedGuests> gin=new ArrayList<>();
		
		//GuestsInNotice gs=new GuestsInNotice();
		getList.forEach(g->{
			VacatedGuests gs=new VacatedGuests();
            gs.setBedId(g.getBedId());
            String name=template.getForObject("http://bedService/bed/getBuildingNameByBuildingId/"+ g.getBuildingId(), String.class);
            gs.setBuildingName(name);
            
            gs.setCheckOutDate(g.getCheckOutDate());
            gs.setEmail(g.getEmail());
            gs.setBedId(g.getBedId());
            gs.setFirstName(g.getFirstName());
            gs.setPersonalNumber(g.getPersonalNumber());
            gs.setId(g.getId());
            gin.add(gs);
		});
		return gin;
   	}

	@Override
	public List<Guest> getTotalPaidByGuestId(String id) {
		// TODO Auto-generated method stub
		return em.createNamedStoredProcedureQuery("totalGuestAmount").setParameter("GUEST__ID" , id).getResultList();
		
	}

	@Override
	public Guest addPostGuest(PreviousGuests guest) {
		String bedUri = "http://bedService/bed/updateBedStatusBydBedId";
		String payUri = "http://paymentService/payment/addPaymentAtOnBoarding";
        Guest g=new Guest();
        
        java.sql.Date createDate = new java.sql.Date(guest.getCreatedOn().getTime());
		g.setCreatedOn(createDate);
		g.setAadharNumber(guest.getAadharNumber());
		g.setAddressLine1(guest.getAddressLine1());
		g.setAddressLine2(guest.getAddressLine2());
		g.setAmountPaid(guest.getAmountPaid());
		g.setAmountToBePaid(guest.getAmountToBePaid());
		g.setBedId(guest.getBedId());
		g.setBloodGroup(guest.getBloodGroup());
		g.setBuildingId(guest.getBuildingId());
		g.setCheckInDate(guest.getCheckInDate());
		g.setCheckinNotes(guest.getCheckinNotes());
		g.setCheckOutDate(guest.getCheckOutDate());
		g.setCity(guest.getCity());
		g.setCreatedBy(guest.getCreatedBy());
		g.setCreatedOn(guest.getCreatedOn());
		g.setDateOfBirth(guest.getDateOfBirth());
		g.setDefaultRent(guest.getDefaultRent());
		g.setDueAmount(guest.getDueAmount());
		g.setDuration(guest.getDuration());
		g.setEmail(guest.getEmail());
		g.setFatherName(guest.getFatherName());
		g.setFatherNumber(guest.getFatherNumber());
		g.setFirstName(guest.getFirstName());
		g.setGender(guest.getGender());
		g.setGuestStatus(guest.getGuestStatus());
		
		g.setLastName(guest.getLastName());
		g.setNoticeDate(guest.getNoticeDate());
		g.setOccupancyType(guest.getOccupancyType());
		g.setOccupation(guest.getOccupation());
		g.setPaymentPurpose(guest.getPaymentPurpose());
		g.setPersonalNumber(guest.getPersonalNumber());
		g.setPincode(guest.getPincode());
		g.setPlannedCheckOutDate(guest.getPlannedCheckOutDate());
		g.setSecondaryPhoneNumber(guest.getSecondaryPhoneNumber());
		g.setSecurityDeposit(guest.getSecurityDeposit());
		g.setState(guest.getState());
		g.setTransactionDate(guest.getTransactionDate());
		g.setTransactionId(guest.getTransactionId());
		g.setWorkAddressLine1(guest.getWorkAddressLine1());
		g.setWorkAddressLine2(guest.getWorkAddressLine2());
		g.setWorkPhone(guest.getWorkPhone());
		repository.save(g);
		

		if (guest.getOccupancyType().equalsIgnoreCase("daily")) {
			java.util.Date m = guest.getCheckInDate();
			Calendar cal = Calendar.getInstance();
			cal.setTime(m);
			cal.add(Calendar.DATE, guest.getDuration());
			m = cal.getTime();
			System.out.println(m);
			g.setPlannedCheckOutDate(m);
			g.setGuestStatus("active");
			repository.save(g);
		} else if (guest.getOccupancyType().equalsIgnoreCase("monthly")) {
			java.util.Date m = guest.getCheckInDate();
			Calendar cal = Calendar.getInstance();
			cal.setTime(m);
			cal.add(Calendar.MONTH, guest.getDuration());
			m = cal.getTime();
			System.out.println(m);

			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
			// System.out.println(dtf.format(m));

			g.setPlannedCheckOutDate(m);
			g.setGuestStatus("active");
			repository.save(g);
		} else {
			g.setGuestStatus("active");

			repository.save(g);
		}

//	        System.out.println(initialDefaultrent); 
		guest.setGuestStatus("active");

		repository.save(g);
		System.out.println(guest.getDueAmount());
		Bed bedReq = new Bed();
		Payment payReq = new Payment();
		// bed setting
		bedReq.setBedId(g.getBedId());

		bedReq.setGuestId(g.getId());
		// bedReq.setDueAmount(guest.getDueAmount());
		template.put(bedUri, bedReq, Bed.class);
		// payment setting
		payReq.setGuestId(g.getId());
		payReq.setBuildingId(g.getBuildingId());
		payReq.setTransactionId(g.getTransactionId());
		payReq.setOccupancyType(g.getOccupancyType());
		payReq.setTransactionDate(g.getTransactionDate());
		// payReq.setCheckinDate(cSqlDate);
		payReq.setAmountPaid(g.getAmountPaid());
		// payReq.setDueAmount(guest.getDueAmount());
		payReq.setPaymentPurpose(g.getPaymentPurpose());
		repository.save(g);
		Payment parRes = template.postForObject(payUri, payReq, Payment.class);
		System.out.println(parRes);
		return g;
	}
	


}
